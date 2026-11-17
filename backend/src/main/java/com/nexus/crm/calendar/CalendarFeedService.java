package com.nexus.crm.calendar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.CurrentUser;

/**
 * Each user can have one private link that calendar apps subscribe to. Anyone holding the link can read that user's
 * follow-ups, so it is a secret: random, shown once, stored only as a hash, and replaceable or removable at any time.
 */
@Service
@RequiredArgsConstructor
public class CalendarFeedService {

	private static final SecureRandom RANDOM = new SecureRandom();

	/** How far back and forward the feed reaches. Calendar apps do not need years of history. */
	private static final Duration PAST = Duration.ofDays(30);

	private static final Duration AHEAD = Duration.ofDays(180);

	private static final int MAX_EVENTS = 2000;

	private static final Duration LENGTH = Duration.ofMinutes(30);

	private final JdbcClient jdbc;

	private final AuditService audit;

	@Value("${nexus.auth.frontend-url}")
	private String frontendUrl;

	@Transactional(readOnly = true)
	public boolean hasFeed(UUID userId) {
		return this.jdbc.sql("select count(*) from calendar_feed_token where user_id = :user").param("user", userId).query(Long.class).single() > 0;
	}

	/** Makes a new link, replacing the old one (which stops working). Returns the secret, which cannot be read again. */
	@Transactional
	public String createOrReplace(CurrentUser user) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		this.jdbc.sql("""
				insert into calendar_feed_token (user_id, token_hash) values (:user, :hash)
				on conflict (user_id) do update set token_hash = excluded.token_hash, created_at = now()
				""").param("user", user.id()).param("hash", hash(token)).update();
		this.audit.record(user.id(), "CalendarFeed", user.id(), AuditLog.Action.UPDATE, AuditService.changes().field("link", "old", "replaced"));
		return token;
	}

	@Transactional
	public void revoke(CurrentUser user) {
		if (this.jdbc.sql("delete from calendar_feed_token where user_id = :user").param("user", user.id()).update() > 0) {
			this.audit.record(user.id(), "CalendarFeed", user.id(), AuditLog.Action.DELETE, null);
		}
	}

	/** The calendar for a link, or empty when the link is unknown or was replaced. */
	@Transactional(readOnly = true)
	public Optional<String> render(String token) {
		return this.jdbc.sql("select user_id from calendar_feed_token where token_hash = :hash")
			.param("hash", hash(token))
			.query(UUID.class)
			.optional()
			.map((userId) -> render(userId, Instant.now()));
	}

	String render(UUID userId, Instant now) {
		record Row(UUID id, String title, String type, String notes, Instant dueAt, String status, Instant updatedAt, int version,
				UUID leadId, UUID contactId, UUID dealId) {
		}
		var rows = this.jdbc.sql("""
				select id, title, type, notes, due_at, status, updated_at, version, lead_id, contact_id, deal_id
				from follow_up
				where assignee_id = :user and deleted_at is null and status <> 'CANCELLED'
				  and due_at between :from and :to
				order by due_at limit :max
				""")
			.param("user", userId)
			.param("from", at(now.minus(PAST)))
			.param("to", at(now.plus(AHEAD)))
			.param("max", MAX_EVENTS)
			.query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("type"), rs.getString("notes"),
					rs.getObject("due_at", OffsetDateTime.class).toInstant(), rs.getString("status"),
					rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getInt("version"), rs.getObject("lead_id", UUID.class),
					rs.getObject("contact_id", UUID.class), rs.getObject("deal_id", UUID.class)))
			.list();

		IcsWriter ics = new IcsWriter().line("BEGIN", "VCALENDAR").line("VERSION", "2.0")
			.line("PRODID", "-//Nexus CRM//Follow-ups//EN").line("CALSCALE", "GREGORIAN").line("METHOD", "PUBLISH")
			.text("X-WR-CALNAME", "Nexus CRM follow-ups").line("REFRESH-INTERVAL;VALUE=DURATION", "PT1H").line("X-PUBLISHED-TTL", "PT1H");
		for (Row r : rows) {
			boolean done = "DONE".equals(r.status());
			String link = (r.dealId() != null) ? "/deals/" + r.dealId() : (r.contactId() != null) ? "/contacts/" + r.contactId()
					: (r.leadId() != null) ? "/leads/" + r.leadId() : "";
			String description = r.type().charAt(0) + r.type().substring(1).toLowerCase() + (r.notes() == null || r.notes().isBlank() ? "" : "\n" + r.notes());
			ics.line("BEGIN", "VEVENT")
				.line("UID", r.id() + "@nexus-crm")
				.time("DTSTAMP", r.updatedAt())
				.time("LAST-MODIFIED", r.updatedAt())
				.line("SEQUENCE", String.valueOf(r.version()))
				.time("DTSTART", r.dueAt())
				.line("DURATION", "PT" + LENGTH.toMinutes() + "M")
				.text("SUMMARY", (done ? "✓ " : "") + r.title())
				.text("DESCRIPTION", description)
				.line("STATUS", "CONFIRMED")
				.line("TRANSP", "TRANSPARENT");
			if (!link.isEmpty()) {
				ics.line("URL", this.frontendUrl + link);
			}
			ics.line("END", "VEVENT");
		}
		return ics.line("END", "VCALENDAR").build();
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
