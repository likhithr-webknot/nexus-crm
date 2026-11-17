package com.nexus.crm.inbox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nexus.crm.mail.GoogleApi;

/**
 * Copies mail to and from known contacts and leads onto their records. It reads each connected mailbox with the
 * read-only Gmail permission, keeps a message only when one of its addresses belongs to a contact or lead, and stores the
 * subject, the people and Gmail's short snippet, never the full text. Everything else is dropped as soon as it is read.
 *
 * <p>First the last 30 days are read, a page at a time over several runs. After that only what is new since the saved
 * history marker is read. If Google no longer has that marker, the last 30 days are read again (messages already
 * stored are skipped, so nothing doubles).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxSyncService {

	private static final String API = "https://gmail.googleapis.com/gmail/v1/users/me";

	/** How many messages one user's run reads. Keeps one big mailbox from using all of the run. */
	private static final int MESSAGES_PER_RUN = 100;

	private static final int USERS_PER_RUN = 10;

	private static final int SNIPPET_MAX = 300;

	private static final int SUBJECT_MAX = 300;

	private static final Set<String> SKIPPED_LABELS = Set.of("SPAM", "TRASH", "DRAFT");

	private final JdbcClient jdbc;

	private final GoogleApi google;

	@Value("${nexus.inbox.sync:true}")
	private boolean enabled;

	@Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
	void scheduled() {
		if (this.enabled && this.google.configured()) {
			syncDue(Instant.now());
		}
	}

	/** Reads the mailboxes that are due. Returns how many messages were stored. */
	public int syncDue(Instant now) {
		List<UUID> users = this.jdbc.sql("""
				update mail_sync_state set last_run_at = :now
				where user_id in (select user_id from mail_sync_state
				                  where last_run_at is null or last_run_at < :due
				                  order by last_run_at nulls first limit :limit for update skip locked)
				returning user_id
				""")
			.param("now", at(now)).param("due", at(now.minusSeconds(240))).param("limit", USERS_PER_RUN)
			.query(UUID.class).list();
		int stored = 0;
		for (UUID user : users) {
			try {
				stored += syncUser(user, now);
			}
			catch (RuntimeException ex) {
				log.warn("Inbox sync failed for a user: {}", ex.getClass().getSimpleName());
				recordError(user, "Sync failed. It will try again shortly.");
			}
		}
		return stored;
	}

	private record State(String historyId, String backfillToken, boolean backfilling) {
	}

	private int syncUser(UUID user, Instant now) {
		String token;
		try {
			token = this.google.accessToken(user, GoogleApi.GMAIL_READ_SCOPE);
		}
		catch (GoogleApi.NotConnected | GoogleApi.Revoked ex) {
			recordError(user, "Inbox sync needs to be connected again.");
			return 0;
		}
		catch (GoogleApi.Unavailable ex) {
			recordError(user, "Google could not be reached. It will try again shortly.");
			return 0;
		}
		String own = this.jdbc.sql("select lower(email::text) from mail_connection where user_id = :user").param("user", user).query(String.class).single();
		State state = this.jdbc.sql("select history_id, backfill_token, backfilling from mail_sync_state where user_id = :user").param("user", user)
			.query((rs, i) -> new State(rs.getString(1), rs.getString(2), rs.getBoolean(3))).single();

		List<String> ids = new ArrayList<>();
		String newHistoryId = state.historyId();
		String nextBackfillToken = null;
		boolean backfilling = state.backfilling();

		if (state.backfilling()) {
			if (state.historyId() == null) {
				// Take the marker before reading, so mail that arrives while reading is picked up by the next run.
				GoogleApi.Response profile = this.google.call("GET", API + "/profile", token, null);
				if (!profile.ok()) {
					return fail(user, profile);
				}
				newHistoryId = string(this.google.parse(profile.body()).get("historyId"));
			}
			String url = API + "/messages?maxResults=" + MESSAGES_PER_RUN + "&q=" + enc("newer_than:30d")
					+ ((state.backfillToken() == null) ? "" : "&pageToken=" + enc(state.backfillToken()));
			GoogleApi.Response page = this.google.call("GET", url, token, null);
			if (!page.ok()) {
				return fail(user, page);
			}
			Map<String, Object> parsed = this.google.parse(page.body());
			ids.addAll(idsOf(parsed.get("messages")));
			nextBackfillToken = string(parsed.get("nextPageToken"));
			backfilling = !nextBackfillToken.isEmpty();
		}
		else {
			String url = API + "/history?startHistoryId=" + enc(state.historyId()) + "&historyTypes=messageAdded&maxResults=" + MESSAGES_PER_RUN;
			GoogleApi.Response page = this.google.call("GET", url, token, null);
			if (page.status() == 404) {
				// Google keeps history for about a week. After a longer gap, read the last 30 days again.
				this.jdbc.sql("update mail_sync_state set history_id = null, backfill_token = null, backfilling = true where user_id = :user").param("user", user).update();
				return 0;
			}
			if (!page.ok()) {
				return fail(user, page);
			}
			Map<String, Object> parsed = this.google.parse(page.body());
			if (parsed.get("history") instanceof List<?> history) {
				for (Object entry : history) {
					if (entry instanceof Map<?, ?> map && map.get("messagesAdded") instanceof List<?> added) {
						for (Object a : added) {
							if (a instanceof Map<?, ?> m && m.get("message") instanceof Map<?, ?> msg && msg.get("id") instanceof String id) {
								ids.add(id);
							}
						}
					}
				}
			}
			newHistoryId = string(parsed.get("historyId"));
			// A long history continues on the next run from where this page ended.
			if (!string(parsed.get("nextPageToken")).isEmpty() && parsed.get("history") instanceof List<?> h && !h.isEmpty()) {
				Object last = h.getLast();
				if (last instanceof Map<?, ?> lastEntry && lastEntry.get("id") instanceof String lastId) {
					newHistoryId = lastId;
				}
			}
		}

		List<Fetched> fetched = new ArrayList<>();
		for (String id : new LinkedHashSet<>(ids)) {
			GoogleApi.Response message = this.google.call("GET", API + "/messages/" + enc(id) + "?format=metadata&metadataHeaders=From&metadataHeaders=To"
					+ "&metadataHeaders=Cc&metadataHeaders=Subject&metadataHeaders=Message-ID", token, null);
			if (message.status() == 404) {
				continue;
			}
			if (!message.ok()) {
				return fail(user, message);
			}
			Fetched parsed = parseMessage(this.google.parse(message.body()), own);
			if (parsed != null) {
				fetched.add(parsed);
			}
		}
		int stored = store(user, fetched);

		this.jdbc.sql("""
				update mail_sync_state set history_id = :history, backfill_token = :token, backfilling = :backfilling,
				  last_synced_at = :now, last_error = null where user_id = :user
				""")
			.param("history", newHistoryId).param("token", (nextBackfillToken == null || nextBackfillToken.isEmpty()) ? null : nextBackfillToken)
			.param("backfilling", backfilling).param("now", at(now)).param("user", user).update();
		return stored;
	}

	/** What is kept from one message before it is matched. */
	record Fetched(String providerId, String threadId, String rfcId, String direction, String from, Set<String> people, String toLine,
			String subject, String snippet, Instant sentAt) {
	}

	/** Reads a Gmail "metadata" message. Returns null for mail that should not be kept (spam, trash, drafts) or has no usable people. */
	@SuppressWarnings("unchecked")
	Fetched parseMessage(Map<String, Object> message, String ownAddress) {
		if (message.get("labelIds") instanceof List<?> labels && labels.stream().anyMatch((l) -> SKIPPED_LABELS.contains(l))) {
			return null;
		}
		Map<String, String> headers = new HashMap<>();
		if (message.get("payload") instanceof Map<?, ?> payload && payload.get("headers") instanceof List<?> list) {
			for (Object h : list) {
				if (h instanceof Map<?, ?> header && header.get("name") instanceof String name && header.get("value") instanceof String value) {
					headers.putIfAbsent(name.toLowerCase(Locale.ROOT), value);
				}
			}
		}
		Set<String> from = MailAddresses.parse(headers.get("from"));
		Set<String> to = MailAddresses.parse(headers.get("to"));
		Set<String> people = new LinkedHashSet<>(from);
		people.addAll(to);
		people.addAll(MailAddresses.parse(headers.get("cc")));
		people.remove(ownAddress);
		if (people.isEmpty() || from.isEmpty() || !(message.get("id") instanceof String id)) {
			return null;
		}
		long millis;
		try {
			millis = Long.parseLong(string(message.get("internalDate")));
		}
		catch (NumberFormatException ex) {
			return null;
		}
		String direction = from.contains(ownAddress) ? "OUT" : "IN";
		String subject = clip(headers.getOrDefault("subject", ""), SUBJECT_MAX);
		return new Fetched(id, string(message.get("threadId")), emptyToNull(clip(headers.get("message-id"), 300)), direction, from.iterator().next(), people,
				String.join(", ", to), subject.isBlank() ? "(no subject)" : subject, clip(string(message.get("snippet")), SNIPPET_MAX), Instant.ofEpochMilli(millis));
	}

	/** Keeps the messages whose people include a contact or lead. One row per message per matching record. */
	private int store(UUID user, List<Fetched> messages) {
		if (messages.isEmpty()) {
			return 0;
		}
		Set<String> addresses = new LinkedHashSet<>();
		messages.forEach((m) -> addresses.addAll(m.people()));
		// lower(...) because the stored addresses are case-insensitive but a bound parameter is plain text.
		Map<String, List<UUID>> contacts = lookup("select id, lower(email::text) from contact where deleted_at is null and lower(email::text) in (:addresses)", addresses);
		Map<String, List<UUID>> leads = lookup("select id, lower(email::text) from lead where deleted_at is null and lower(email::text) in (:addresses)", addresses);

		int stored = 0;
		for (Fetched m : messages) {
			Set<UUID> contactIds = new LinkedHashSet<>();
			Set<UUID> leadIds = new LinkedHashSet<>();
			for (String person : m.people()) {
				contactIds.addAll(contacts.getOrDefault(person, List.of()));
				leadIds.addAll(leads.getOrDefault(person, List.of()));
			}
			for (UUID contact : contactIds) {
				stored += insert(user, m, contact, null);
			}
			for (UUID lead : leadIds) {
				stored += insert(user, m, null, lead);
			}
		}
		return stored;
	}

	private Map<String, List<UUID>> lookup(String sql, Set<String> addresses) {
		Map<String, List<UUID>> found = new HashMap<>();
		this.jdbc.sql(sql).param("addresses", addresses)
			.query((rs, i) -> {
				found.computeIfAbsent(rs.getString(2), (k) -> new ArrayList<>()).add(rs.getObject(1, UUID.class));
				return null;
			}).list();
		return found;
	}

	private int insert(UUID user, Fetched m, UUID contact, UUID lead) {
		return this.jdbc.sql("""
				insert into email_message (id, user_id, provider_message_id, thread_id, rfc_message_id, direction, from_addr, to_addrs, subject, snippet, sent_at, contact_id, lead_id)
				values (:id, :user, :provider, :thread, :rfc, :direction, :from, :to, :subject, :snippet, :sent, :contact, :lead)
				on conflict do nothing
				""")
			.param("id", UUID.randomUUID()).param("user", user).param("provider", m.providerId()).param("thread", emptyToNull(m.threadId()))
			.param("rfc", m.rfcId()).param("direction", m.direction()).param("from", m.from()).param("to", m.toLine())
			.param("subject", m.subject()).param("snippet", m.snippet()).param("sent", at(m.sentAt()))
			.param("contact", contact).param("lead", lead)
			.update();
	}

	private int fail(UUID user, GoogleApi.Response response) {
		recordError(user, (response.status() == 429 || response.status() >= 500)
				? "Google asked us to slow down (HTTP " + response.status() + "). It will try again shortly."
				: "Google refused the request (HTTP " + response.status() + "). Try connecting again.");
		return 0;
	}

	private void recordError(UUID user, String message) {
		this.jdbc.sql("update mail_sync_state set last_error = :message where user_id = :user").param("message", message).param("user", user).update();
	}

	private static List<String> idsOf(Object messages) {
		List<String> ids = new ArrayList<>();
		if (messages instanceof List<?> list) {
			for (Object m : list) {
				if (m instanceof Map<?, ?> map && map.get("id") instanceof String id) {
					ids.add(id);
				}
			}
		}
		return ids;
	}

	private static String string(Object value) {
		return (value instanceof String s) ? s : "";
	}

	private static String clip(String value, int max) {
		if (value == null) {
			return "";
		}
		String clean = value.replaceAll("\\p{Cntrl}", " ").strip();
		return (clean.length() <= max) ? clean : clean.substring(0, max);
	}

	private static String emptyToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
