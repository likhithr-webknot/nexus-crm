package com.nexus.crm.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.mail.GoogleApi;
import com.nexus.crm.shared.CurrentUser;

/**
 * Pushes each connected user's follow-ups into their Google Calendar, one way. A follow-up becomes a 30-minute event
 * whose id is made from the follow-up's id, so writing it twice never makes a duplicate. Edits made in Google are not
 * read back. A user who deletes an event in Google keeps it deleted until the follow-up itself changes.
 *
 * <p>Nothing here holds a database transaction while talking to Google. Each run claims its users with an update, so
 * two app instances never work on the same user at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

	private static final String EVENTS = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

	private static final Duration LENGTH = Duration.ofMinutes(30);

	/** New follow-ups are pushed when due from a week ago to three months ahead. */
	private static final Duration PAST = Duration.ofDays(7);

	private static final Duration AHEAD = Duration.ofDays(90);

	/** The most events written or removed for one user in one run. The rest wait for the next run. */
	private static final int BATCH = 100;

	private static final int USERS_PER_RUN = 20;

	private final JdbcClient jdbc;

	private final GoogleApi google;

	private final JsonMapper json;

	private final AuditService audit;

	private final TransactionTemplate transactions;

	@Value("${nexus.auth.frontend-url}")
	private String frontendUrl;

	@Value("${nexus.calendar.google-sync:true}")
	private boolean enabled;

	@Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
	void scheduled() {
		if (this.enabled && this.google.configured()) {
			syncDue(Instant.now());
		}
	}

	/** Syncs the users who are due. Returns how many events were written or removed. */
	public int syncDue(Instant now) {
		List<UUID> users = this.jdbc.sql("""
				update calendar_google_sync set last_run_at = :now
				where user_id in (select user_id from calendar_google_sync
				                  where last_run_at is null or last_run_at < :due
				                  order by last_run_at nulls first limit :limit for update skip locked)
				returning user_id
				""")
			.param("now", at(now)).param("due", at(now.minusSeconds(50))).param("limit", USERS_PER_RUN)
			.query(UUID.class).list();
		int changed = 0;
		for (UUID user : users) {
			try {
				changed += syncUser(user, now);
			}
			catch (RuntimeException ex) {
				// One user's problem must not stop the others.
				log.warn("Calendar sync failed for a user: {}", ex.getClass().getSimpleName());
				recordError(user, "Sync failed. It will try again shortly.");
			}
		}
		return changed;
	}

	private int syncUser(UUID user, Instant now) {
		String token;
		try {
			token = this.google.accessToken(user, GoogleApi.CALENDAR_SCOPE);
		}
		catch (GoogleApi.NotConnected | GoogleApi.Revoked ex) {
			recordError(user, "Google Calendar needs to be connected again.");
			return 0;
		}
		catch (GoogleApi.Unavailable ex) {
			recordError(user, "Google could not be reached. It will try again shortly.");
			return 0;
		}

		int changed = 0;
		for (Removal removal : removals(user)) {
			GoogleApi.Response response = this.google.call("DELETE", EVENTS + "/" + removal.eventId(), token, null);
			if (response.ok() || response.status() == 404 || response.status() == 410) {
				this.jdbc.sql("delete from calendar_link where follow_up_id = :id and user_id = :user")
					.param("id", removal.followUpId()).param("user", user).update();
				changed++;
			}
			else if (shouldRetryLater(response)) {
				return stop(user, changed, response);
			}
			else {
				log.warn("Google refused to remove a calendar event: status={}", response.status());
				this.jdbc.sql("delete from calendar_link where follow_up_id = :id and user_id = :user")
					.param("id", removal.followUpId()).param("user", user).update();
			}
		}

		for (Change change : changes(user, now)) {
			String eventId = (change.eventId() != null) ? change.eventId() : eventIdFor(change.id());
			Map<String, Object> body = eventBody(change);
			GoogleApi.Response response;
			if (change.eventId() == null) {
				Map<String, Object> created = new LinkedHashMap<>(body);
				created.put("id", eventId);
				response = this.google.call("POST", EVENTS, token, this.json.writeValueAsString(created));
				if (response.status() == 409) {
					// The event exists already (for example it was removed earlier, and Google keeps removed ids). Put it back.
					Map<String, Object> restored = new LinkedHashMap<>(body);
					restored.put("status", "confirmed");
					response = this.google.call("PATCH", EVENTS + "/" + eventId, token, this.json.writeValueAsString(restored));
				}
			}
			else {
				response = this.google.call("PATCH", EVENTS + "/" + eventId, token, this.json.writeValueAsString(body));
				if (response.status() == 404 || response.status() == 410) {
					// The user deleted the event in Google. Leave it deleted.
					markSynced(change, user, eventId);
					continue;
				}
			}
			if (response.ok()) {
				markSynced(change, user, eventId);
				changed++;
			}
			else if (shouldRetryLater(response)) {
				return stop(user, changed, response);
			}
			else {
				// Google will not take this one (for example a title it rejects). Skip it so it does not block the rest.
				log.warn("Google refused a calendar event: status={}", response.status());
				markSynced(change, user, eventId);
			}
		}

		this.jdbc.sql("update calendar_google_sync set last_synced_at = :now, last_error = null where user_id = :user")
			.param("now", at(now)).param("user", user).update();
		return changed;
	}

	/** Removes the user's events from Google (as far as possible) and turns sync off. */
	public void disconnect(CurrentUser user) {
		try {
			String token = this.google.accessToken(user.id(), GoogleApi.CALENDAR_SCOPE);
			this.jdbc.sql("select google_event_id from calendar_link where user_id = :user limit 500").param("user", user.id()).query(String.class).list()
				.forEach((eventId) -> this.google.call("DELETE", EVENTS + "/" + eventId, token, null));
		}
		catch (RuntimeException ex) {
			// If Google cannot be reached or the permission is gone, the events stay. Sync is still turned off.
			log.info("Calendar events were not removed from Google on disconnect: {}", ex.getClass().getSimpleName());
		}
		forget(user);
	}

	private void forget(CurrentUser user) {
		this.transactions.executeWithoutResult((tx) -> {
			this.jdbc.sql("delete from calendar_link where user_id = :user").param("user", user.id()).update();
			if (this.jdbc.sql("delete from calendar_google_sync where user_id = :user").param("user", user.id()).update() > 0) {
				this.audit.record(user.id(), "CalendarSync", user.id(), AuditLog.Action.DELETE, null);
			}
		});
	}

	private int stop(UUID user, int changed, GoogleApi.Response response) {
		recordError(user, "Google asked us to slow down (HTTP " + response.status() + "). It will try again shortly.");
		return changed;
	}

	/** Rate limits, expired tokens and server errors are worth another try; other 4xx answers will not change. */
	private static boolean shouldRetryLater(GoogleApi.Response response) {
		return response.status() == 401 || response.status() == 429 || response.status() >= 500
				|| (response.status() == 403 && response.body().contains("ateLimit"));
	}

	private record Removal(UUID followUpId, String eventId) {
	}

	private record Change(UUID id, String title, String type, String notes, Instant dueAt, String status, int version, UUID leadId,
			UUID contactId, UUID dealId, String eventId) {
	}

	/** Events whose follow-up was deleted, cancelled or given to someone else. */
	private List<Removal> removals(UUID user) {
		return this.jdbc.sql("""
				select l.follow_up_id, l.google_event_id
				from calendar_link l left join follow_up f on f.id = l.follow_up_id
				where l.user_id = :user
				  and (f.id is null or f.deleted_at is not null or f.status = 'CANCELLED' or f.assignee_id <> :user)
				limit :batch
				""")
			.param("user", user).param("batch", BATCH)
			.query((rs, i) -> new Removal(rs.getObject(1, UUID.class), rs.getString(2)))
			.list();
	}

	/** Follow-ups with no event yet (inside the window), and ones changed since their event was written. */
	private List<Change> changes(UUID user, Instant now) {
		return this.jdbc.sql("""
				select f.id, f.title, f.type, f.notes, f.due_at, f.status, f.version, f.lead_id, f.contact_id, f.deal_id, l.google_event_id
				from follow_up f left join calendar_link l on l.follow_up_id = f.id and l.user_id = :user
				where f.assignee_id = :user and f.deleted_at is null and f.status <> 'CANCELLED'
				  and ((l.follow_up_id is null and f.due_at between :from and :to) or l.synced_version <> f.version)
				order by f.due_at limit :batch
				""")
			.param("user", user).param("from", at(now.minus(PAST))).param("to", at(now.plus(AHEAD))).param("batch", BATCH)
			.query((rs, i) -> new Change(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("type"), rs.getString("notes"),
					rs.getObject("due_at", OffsetDateTime.class).toInstant(), rs.getString("status"), rs.getInt("version"),
					rs.getObject("lead_id", UUID.class), rs.getObject("contact_id", UUID.class), rs.getObject("deal_id", UUID.class),
					rs.getString("google_event_id")))
			.list();
	}

	private Map<String, Object> eventBody(Change c) {
		boolean done = "DONE".equals(c.status());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("summary", (done ? "✓ " : "") + c.title());
		body.put("description", c.type().charAt(0) + c.type().substring(1).toLowerCase() + ((c.notes() == null || c.notes().isBlank()) ? "" : "\n\n" + c.notes()));
		body.put("start", Map.of("dateTime", DateTimeFormatter.ISO_INSTANT.format(c.dueAt()), "timeZone", "UTC"));
		body.put("end", Map.of("dateTime", DateTimeFormatter.ISO_INSTANT.format(c.dueAt().plus(LENGTH)), "timeZone", "UTC"));
		body.put("transparency", "transparent");
		body.put("reminders", Map.of("useDefault", true));
		body.put("extendedProperties", Map.of("private", Map.of("nexusFollowUp", c.id().toString())));
		String link = (c.dealId() != null) ? "/deals/" + c.dealId() : (c.contactId() != null) ? "/contacts/" + c.contactId()
				: (c.leadId() != null) ? "/leads/" + c.leadId() : null;
		if (link != null) {
			body.put("source", Map.of("title", "Nexus CRM", "url", this.frontendUrl + link));
		}
		return body;
	}

	private void markSynced(Change change, UUID user, String eventId) {
		this.jdbc.sql("""
				insert into calendar_link (follow_up_id, user_id, google_event_id, synced_version) values (:id, :user, :event, :version)
				on conflict (follow_up_id, user_id) do update set google_event_id = excluded.google_event_id,
				  synced_version = excluded.synced_version, synced_at = now()
				""")
			.param("id", change.id()).param("user", user).param("event", eventId).param("version", change.version()).update();
	}

	private void recordError(UUID user, String message) {
		this.jdbc.sql("update calendar_google_sync set last_error = :message where user_id = :user").param("message", message).param("user", user).update();
	}

	/** Google event ids use the letters a-v and digits. "nc" plus the follow-up's id in hex fits and is the same every time. */
	static String eventIdFor(UUID followUpId) {
		return "nc" + followUpId.toString().replace("-", "");
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
