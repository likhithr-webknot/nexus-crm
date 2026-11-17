package com.nexus.crm.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The hourly alerts: who is told, that they are told once, and that new activity re-arms a deal. */
class AlertServiceTests extends IntegrationTest {

	@Autowired
	private AlertService alerts;

	@Autowired
	private JdbcClient jdbc;

	private MvcTestResult send(MockMvcRequestBuilder request, Session session, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String field(String json, String name) {
		return json.replaceAll("^.*?\"" + name + "\":\"([^\"]+)\".*$", "$1");
	}

	private String notificationsOf(Session session) {
		return json(this.mvc.get().uri("/api/v1/notifications?size=50").cookie(session.accessToken()).exchange());
	}

	private String newDeal(Session owner) {
		String companyId = field(json(send(this.mvc.post().uri("/api/v1/companies"), owner, "{\"name\":\"Alert Co " + UUID.randomUUID() + "\"}")), "id");
		return field(json(send(this.mvc.post().uri("/api/v1/deals"), owner,
				"{\"name\":\"Quiet deal\",\"companyId\":\"" + companyId + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}")), "id");
	}

	private void makeQuiet(String dealId, int days) {
		this.jdbc.sql("update deal set created_at = now() - make_interval(days => :d) where id = cast(:id as uuid)")
			.param("d", days).param("id", dealId).update();
		this.jdbc.sql("update deal_stage_history set changed_at = now() - make_interval(days => :d) where deal_id = cast(:id as uuid)")
			.param("d", days).param("id", dealId).update();
		// Creating a deal also logs a "created" activity. It ages with the deal.
		this.jdbc.sql("update activity set occurred_at = now() - make_interval(days => :d) where deal_id = cast(:id as uuid)")
			.param("d", days).param("id", dealId).update();
	}

	private boolean dealAlerted(String dealId) {
		return Boolean.TRUE.equals(this.jdbc.sql("select stale_alerted_at is not null from deal where id = cast(:id as uuid)").param("id", dealId)
			.query(Boolean.class).single());
	}

	private Instant dealAlertedAt(String dealId) {
		return this.jdbc.sql("select stale_alerted_at from deal where id = cast(:id as uuid)").param("id", dealId)
			.query((rs, i) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant()).single();
	}

	private boolean followUpAlerted(String followUpId) {
		return Boolean.TRUE.equals(this.jdbc.sql("select overdue_alerted_at is not null from follow_up where id = cast(:id as uuid)")
			.param("id", followUpId).query(Boolean.class).single());
	}

	private Instant followUpAlertedAt(String followUpId) {
		return this.jdbc.sql("select overdue_alerted_at from follow_up where id = cast(:id as uuid)").param("id", followUpId)
			.query((rs, i) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant()).single();
	}

	private String newFollowUp(Session rep, String dealId, String title) {
		return field(json(send(this.mvc.post().uri("/api/v1/follow-ups"), rep,
				"{\"title\":\"" + title + "\",\"dueAt\":\"" + Instant.now().plus(Duration.ofHours(1)) + "\",\"dealId\":\"" + dealId + "\"}")), "id");
	}

	// These tests share one database, and a run looks at every row in it. So each test checks only its own rows.

	@Test
	void aQuietDealAlertsItsOwnerOnceAndTheTeamLeads() {
		Session rep = signedIn(Role.REP);
		Session admin = signedIn(Role.ADMIN);
		String deal = newDeal(rep);
		makeQuiet(deal, 30);

		this.alerts.run(Instant.now());

		assertThat(dealAlerted(deal)).isTrue();
		assertThat(notificationsOf(rep)).contains("DEAL_STALE").contains("had no activity for 14+ days");
		assertThat(notificationsOf(admin)).contains("on your team");

		// A second run does not tell the owner again.
		String before = notificationsOf(rep);
		Instant firstAlert = dealAlertedAt(deal);
		this.alerts.run(Instant.now());
		assertThat(notificationsOf(rep)).isEqualTo(before);
		assertThat(dealAlertedAt(deal)).isEqualTo(firstAlert);
	}

	@Test
	void aNewDealIsNotAlertedBecauseItIsNotQuiet() {
		Session rep = signedIn(Role.REP);
		String deal = newDeal(rep);

		this.alerts.run(Instant.now());

		assertThat(dealAlerted(deal)).isFalse();
	}

	@Test
	void aDealThatSawActivityAfterItsAlertIsAlertedAgainWhenItGoesQuietAgain() {
		Session rep = signedIn(Role.REP);
		String deal = newDeal(rep);
		makeQuiet(deal, 40);
		this.alerts.run(Instant.now());
		Instant firstAlert = dealAlertedAt(deal);

		// The first alert was 30 days ago, then someone logged a call 20 days ago, and nothing since.
		this.jdbc.sql("update deal set stale_alerted_at = now() - interval '30 days' where id = cast(:id as uuid)").param("id", deal).update();
		this.jdbc.sql("insert into activity (id, type, subject, occurred_at, deal_id) values (:id, 'NOTE', 'Called them', now() - interval '20 days', cast(:deal as uuid))")
			.param("id", UUID.randomUUID()).param("deal", deal).update();
		this.alerts.run(Instant.now());

		assertThat(dealAlertedAt(deal)).isAfter(firstAlert.minus(Duration.ofMinutes(1)));
		assertThat(dealAlerted(deal)).isTrue();
	}

	@Test
	void aDealWithRecentActivityIsLeftAlone() {
		Session rep = signedIn(Role.REP);
		String deal = newDeal(rep);
		makeQuiet(deal, 40);
		this.jdbc.sql("insert into activity (id, type, subject, occurred_at, deal_id) values (:id, 'NOTE', 'Called them', now() - interval '2 days', cast(:deal as uuid))")
			.param("id", UUID.randomUUID()).param("deal", deal).update();

		this.alerts.run(Instant.now());

		assertThat(dealAlerted(deal)).isFalse();
	}

	@Test
	void closedDealsAreNeverAlerted() {
		Session rep = signedIn(Role.REP);
		String deal = newDeal(rep);
		makeQuiet(deal, 60);
		this.jdbc.sql("update deal set closed_at = now() where id = cast(:id as uuid)").param("id", deal).update();

		this.alerts.run(Instant.now());

		assertThat(dealAlerted(deal)).isFalse();
	}

	@Test
	void anOverdueFollowUpAlertsItsAssigneeOnceAndAgainOnlyAfterItsDueDateMoves() {
		Session rep = signedIn(Role.REP);
		String followUp = newFollowUp(rep, newDeal(rep), "Call back");
		this.jdbc.sql("update follow_up set due_at = now() - interval '3 days' where id = cast(:id as uuid)").param("id", followUp).update();

		this.alerts.run(Instant.now());

		assertThat(followUpAlerted(followUp)).isTrue();
		assertThat(notificationsOf(rep)).contains("FOLLOW_UP_OVERDUE").contains("overdue");
		Instant firstAlert = followUpAlertedAt(followUp);
		String before = notificationsOf(rep);
		this.alerts.run(Instant.now());
		assertThat(followUpAlertedAt(followUp)).isEqualTo(firstAlert);
		assertThat(notificationsOf(rep)).isEqualTo(before);

		// The due date was moved to a later time, and that has passed too: it alerts again.
		this.jdbc.sql("update follow_up set due_at = now() - interval '2 days', overdue_alerted_at = now() - interval '3 days' where id = cast(:id as uuid)")
			.param("id", followUp).update();
		this.alerts.run(Instant.now());
		assertThat(followUpAlertedAt(followUp)).isAfter(firstAlert.minus(Duration.ofMinutes(1)));
		assertThat(followUpAlertedAt(followUp)).isAfter(Instant.now().minus(Duration.ofDays(1)));
	}

	@Test
	void aFollowUpThatIsNotYetOverdueByTheGraceTimeIsLeftAlone() {
		Session rep = signedIn(Role.REP);
		String followUp = newFollowUp(rep, newDeal(rep), "Soon");
		this.jdbc.sql("update follow_up set due_at = now() - interval '2 hours' where id = cast(:id as uuid)").param("id", followUp).update();

		this.alerts.run(Instant.now());

		assertThat(followUpAlerted(followUp)).isFalse();
	}

	@Test
	void aDoneFollowUpIsNeverAlerted() {
		Session rep = signedIn(Role.REP);
		String followUp = newFollowUp(rep, newDeal(rep), "Finished");
		this.jdbc.sql("update follow_up set due_at = now() - interval '5 days', status = 'DONE', completed_at = now() - interval '4 days' where id = cast(:id as uuid)")
			.param("id", followUp).update();

		this.alerts.run(Instant.now());

		assertThat(followUpAlerted(followUp)).isFalse();
	}

}
