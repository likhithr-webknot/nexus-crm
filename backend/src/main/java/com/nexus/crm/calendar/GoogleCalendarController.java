package com.nexus.crm.calendar;

import java.time.OffsetDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.mail.MailService;
import com.nexus.crm.shared.CurrentUser;

/** Status and disconnect for the Google Calendar push. Connecting goes through {@code POST /mail/connect?feature=calendar}. */
@RestController
@RequestMapping("/api/v1/calendar/google")
@RequiredArgsConstructor
class GoogleCalendarController {

	private final MailService mail;

	private final CalendarSyncService sync;

	private final JdbcClient jdbc;

	record GoogleStatus(boolean configured, boolean connected, boolean needsReconnect, String email, OffsetDateTime lastSyncedAt, String lastError) {
	}

	@GetMapping
	GoogleStatus status(@AuthenticationPrincipal CurrentUser user) {
		MailService.Status account = this.mail.status(user.id());
		var state = this.jdbc.sql("select last_synced_at, last_error from calendar_google_sync where user_id = :user")
			.param("user", user.id())
			.query((rs, i) -> new Object[] { rs.getObject(1, OffsetDateTime.class), rs.getString(2) })
			.optional();
		boolean syncing = state.isPresent();
		return new GoogleStatus(account.configured(), syncing && account.calendar(), syncing && account.needsReconnect(), account.email(),
				syncing ? (OffsetDateTime) state.get()[0] : null, syncing ? (String) state.get()[1] : null);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect(@AuthenticationPrincipal CurrentUser user) {
		this.sync.disconnect(user);
	}

}
