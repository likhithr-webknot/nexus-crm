package com.nexus.crm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Each Google feature (mail, calendar, inbox) asks for its own permission only. */
@TestPropertySource(properties = { "GOOGLE_CLIENT_ID=test-client", "GOOGLE_CLIENT_SECRET=test-secret",
		"NEXUS_MAIL_REDIRECT_URI=http://localhost:8080/api/v1/mail/callback",
		"NEXUS_MAIL_TOKEN_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=" })
class MailConsentTests extends IntegrationTest {

	@Autowired
	private JdbcClient jdbc;

	private String connect(Session session, String query) {
		MvcTestResult result = withCsrf(this.mvc.post().uri("/api/v1/mail/connect" + query)).cookie(session.accessToken()).exchange();
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	@Test
	void mailAsksOnlyToSend() {
		String url = connect(signedIn(Role.REP), "");

		assertThat(url).contains("gmail.send").doesNotContain("calendar").doesNotContain("gmail.readonly");
	}

	@Test
	void calendarAsksOnlyForCalendarEventsAndKeepsEarlierPermissions() {
		String url = connect(signedIn(Role.REP), "?feature=calendar");

		assertThat(url).contains("calendar.events").doesNotContain("gmail.send").doesNotContain("gmail.readonly");
		// Google adds the permissions the user gave before, so asking for calendar does not drop mail.
		assertThat(url).contains("include_granted_scopes=true").contains("access_type=offline");
	}

	@Test
	void inboxAsksOnlyToRead() {
		String url = connect(signedIn(Role.REP), "?feature=inbox");

		assertThat(url).contains("gmail.readonly").doesNotContain("gmail.send").doesNotContain("calendar");
	}

	@Test
	void theFeatureIsRememberedForTheReturnTrip() {
		connect(signedIn(Role.REP), "?feature=calendar");

		assertThat(this.jdbc.sql("select count(*) from mail_oauth_state where feature = 'calendar'").query(Long.class).single()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void anUnknownFeatureIsRefused() {
		Session rep = signedIn(Role.REP);

		assertThat(withCsrf(this.mvc.post().uri("/api/v1/mail/connect?feature=contacts")).cookie(rep.accessToken()).exchange()).hasStatus(409);
	}

	@Test
	void aReturnTripWithAnUnknownStateIsRefusedAndConnectsNothing() {
		assertThat(this.mvc.get().uri("/api/v1/mail/callback?code=abc&state=never-issued").exchange()).hasStatus(409);
	}

	@Test
	void statusShowsEachPermissionSeparately() {
		Session rep = signedIn(Role.REP);

		String status = new String(this.mvc.get().uri("/api/v1/mail/status").cookie(rep.accessToken()).exchange().getResponse()
			.getContentAsByteArray(), StandardCharsets.UTF_8);

		assertThat(status).contains("\"connected\":false").contains("\"calendar\":false").contains("\"inbox\":false").contains("\"configured\":true");
	}

}
