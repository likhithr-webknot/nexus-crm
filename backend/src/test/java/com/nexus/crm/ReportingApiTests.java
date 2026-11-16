package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The dashboard and report queries run against a real database, and respect who may see what. */
class ReportingApiTests extends IntegrationTest {

	private MvcTestResult post(String uri, Session session, String body) {
		return withCsrf(this.mvc.post().uri(uri)).cookie(session.accessToken()).contentType(MediaType.APPLICATION_JSON)
			.content(body).exchange();
	}

	private MvcTestResult get(String uri, Session session) {
		return this.mvc.get().uri(uri).cookie(session.accessToken()).exchange();
	}

	@Test
	void dashboardCountsANewLeadAndListsItsSource() {
		Session rep = signedIn(Role.REP);
		assertThat(post("/api/v1/leads", rep, "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"" + uniqueEmail()
				+ "\",\"source\":\"REFERRAL\"}")).hasStatus(201);

		MvcTestResult result = get("/api/v1/dashboard/summary", rep);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.data.kpis[1].id").isEqualTo("new");
		assertThat(result).bodyJson().extractingPath("$.data.kpis[1].value").asInstanceOf(
				org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isGreaterThanOrEqualTo(1.0);
		assertThat(result).bodyJson().extractingPath("$.data.kpis").asArray().hasSize(7);
		assertThat(result).bodyJson().extractingPath("$.data.leadSources[*].source").asArray().contains("REFERRAL");
		assertThat(result).bodyJson().extractingPath("$.data.pipeline").asArray().hasSize(4);
	}

	@Test
	void reportsPageEndpointsAnswer() {
		Session admin = signedIn(Role.ADMIN);

		assertThat(get("/api/v1/reports/overview", admin)).hasStatusOk()
			.bodyJson().extractingPath("$.funnel").asArray().hasSize(5);
		assertThat(get("/api/v1/reports/revenue?months=6", admin)).hasStatusOk()
			.bodyJson().extractingPath("$").asArray().hasSize(6);
	}

	@Test
	void onlyAdminsSetTargetsAndOnlyAdminsSeeThem() {
		String month = LocalDate.now(ZoneOffset.UTC).plusYears(5).withMonth(3).toString().substring(0, 7);

		MvcTestResult denied = withCsrf(this.mvc.put().uri("/api/v1/revenue-targets/" + month))
			.cookie(signedIn(Role.MANAGER).accessToken()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":\"2500000\"}").exchange();
		assertThat(denied).hasStatus(403);

		Session admin = signedIn(Role.ADMIN);
		MvcTestResult saved = withCsrf(this.mvc.put().uri("/api/v1/revenue-targets/" + month)).cookie(admin.accessToken())
			.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"2500000\"}").exchange();
		assertThat(saved).hasStatusOk();
		assertThat(get("/api/v1/revenue-targets?year=" + month.substring(0, 4), admin)).hasStatusOk()
			.bodyJson().extractingPath("$[0].amount").isEqualTo("2500000.00");
	}

	@Test
	void anInvalidRangeIsRejected() {
		assertThat(get("/api/v1/dashboard/summary?from=2026-05-02&to=2026-05-01", signedIn(Role.REP))).hasStatus(422);
	}

}
