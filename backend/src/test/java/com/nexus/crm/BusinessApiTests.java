package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The business endpoints, end to end: who sees what, and the flows from lead to won deal. */
class BusinessApiTests extends IntegrationTest {

	@Test
	void aRepSeesTheirOwnLeadsButNotAnotherRepsLeads() {
		Session alice = signedIn(Role.REP);
		Session bob = signedIn(Role.REP);
		String leadId = createLead(alice, uniqueEmail());

		assertThat(get("/api/v1/leads/" + leadId, alice)).hasStatusOk();
		// Someone else's lead is "not found", so its existence is not revealed.
		assertThat(get("/api/v1/leads/" + leadId, bob)).hasStatus(404);
		assertThat(get("/api/v1/leads?q=" + leadId, bob)).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(0);
	}

	@Test
	void anAdminSeesEveryonesLeads() {
		String leadId = createLead(signedIn(Role.REP), uniqueEmail());
		assertThat(get("/api/v1/leads/" + leadId, signedIn(Role.ADMIN))).hasStatusOk();
	}

	@Test
	void aSecondOpenLeadWithTheSameEmailIsADuplicate() {
		Session rep = signedIn(Role.REP);
		String email = uniqueEmail();
		createLead(rep, email);

		assertThat(post("/api/v1/leads", rep, leadJson(email.toUpperCase()))).hasStatus(409)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("duplicate");
	}

	@Test
	void leadValidationNamesTheBadFieldsAndCurrencyRules() {
		Session rep = signedIn(Role.REP);
		assertThat(post("/api/v1/leads", rep, "{\"firstName\":\"\",\"email\":\"nope\"}")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.errors[*].field")
			.asArray()
			.contains("firstName", "email");

		String body = "{\"firstName\":\"A\",\"estimatedValue\":{\"amount\":\"10\",\"currency\":\"XYZ\"}}";
		assertThat(post("/api/v1/leads", rep, body)).hasStatus(422)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("unsupported_currency");
		body = "{\"firstName\":\"A\",\"estimatedValue\":{\"amount\":\"10.123\",\"currency\":\"INR\"}}";
		assertThat(post("/api/v1/leads", rep, body)).hasStatus(422)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("invalid_precision");
	}

	@Test
	void importingSkipsBadRowsAndReportsThem() {
		Session manager = signedIn(Role.MANAGER);
		String good = uniqueEmail();
		String body = "{\"rows\":[{\"name\":\"Good Row\",\"email\":\"" + good + "\"},"
				+ "{\"name\":\"\",\"email\":\"x@y.example\"},"
				+ "{\"name\":\"Repeat\",\"email\":\"" + good + "\"},"
				+ "{\"name\":\"Bad Mail\",\"email\":\"nope\"}]}";

		MvcTestResult result = post("/api/v1/leads/imports", manager, body);

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.imported").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.errors.length()").isEqualTo(3);
	}

	@Test
	void changingStatusWritesTheTimelineAndDisqualifyingNeedsAReason() {
		Session rep = signedIn(Role.REP);
		String leadId = createLead(rep, uniqueEmail());

		assertThat(post("/api/v1/leads/" + leadId + "/status", rep, "{\"status\":\"DISQUALIFIED\"}")).hasStatus(422);
		assertThat(post("/api/v1/leads/" + leadId + "/status", rep, "{\"status\":\"QUALIFIED\"}")).hasStatusOk();

		assertThat(get("/api/v1/activities?leadId=" + leadId, rep)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.items[0].subject")
			.isEqualTo("Status changed to Qualified");
	}

	@Test
	void repsCannotReassignLeadsButManagersAndAdminsCan() {
		Session rep = signedIn(Role.REP);
		String leadId = createLead(rep, uniqueEmail());
		assertThat(post("/api/v1/leads/" + leadId + "/assign", rep, "{\"ownerId\":null}")).hasStatus(403);

		Session admin = signedIn(Role.ADMIN);
		assertThat(post("/api/v1/leads/" + leadId + "/assign", admin, "{\"ownerId\":null}")).hasStatusOk();
	}

	@Test
	void assigningALeadNotifiesTheNewOwner() {
		String email = uniqueEmail();
		AppUser owner = invite(email, Role.REP);
		Session ownerSession = signIn(uniqueGoogleId(), email);
		Session admin = signedIn(Role.ADMIN);
		String leadId = createLead(admin, uniqueEmail());

		assertThat(post("/api/v1/leads/" + leadId + "/assign", admin, "{\"ownerId\":\"" + owner.getId() + "\"}")).hasStatusOk();

		assertThat(get("/api/v1/notifications/unread-count", ownerSession)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.count")
			.isEqualTo(1);
		assertThat(withCsrf(this.mvc.post().uri("/api/v1/notifications/read")).cookie(ownerSession.accessToken()).exchange())
			.hasStatus(204);
		assertThat(get("/api/v1/notifications/unread-count", ownerSession)).bodyJson().extractingPath("$.count").isEqualTo(0);
	}

	@Test
	void convertingALeadCreatesCompanyContactAndDealTogether() {
		Session rep = signedIn(Role.REP);
		String email = uniqueEmail();
		String leadId = createLead(rep, email);

		MvcTestResult converted = post("/api/v1/leads/" + leadId + "/convert", rep, "{}");

		assertThat(converted).hasStatusOk();
		String dealId = JsonPath.read(body(converted), "$.dealId");
		assertThat(get("/api/v1/deals/" + dealId, rep)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.stageCode")
			.isEqualTo("QUALIFIED");
		assertThat(get("/api/v1/leads/" + leadId, rep)).bodyJson().extractingPath("$.status").isEqualTo("CONVERTED");
		// A converted lead cannot be converted again.
		assertThat(post("/api/v1/leads/" + leadId + "/convert", rep, "{}")).hasStatus(409);
		// The contact now exists and is visible.
		assertThat(get("/api/v1/contacts?q=" + email, rep)).bodyJson().extractingPath("$.total").isEqualTo(1);
	}

	@Test
	void winningADealSavesItsInrValueAndClosesIt() {
		Session rep = signedIn(Role.REP);
		String dealId = createDeal(rep, "1500.50");

		MvcTestResult won = patch("/api/v1/deals/" + dealId + "/stage", rep, "{\"stageCode\":\"WON\"}");

		assertThat(won).hasStatusOk().bodyJson().extractingPath("$.baseAmount").isEqualTo("1500.5000");
		assertThat(won).bodyJson().extractingPath("$.closedAt").isNotNull();
		assertThat(patch("/api/v1/deals/" + dealId + "/stage", rep, "{\"stageCode\":\"DEMO\"}")).hasStatus(409);
	}

	@Test
	void losingADealNeedsAReason() {
		Session rep = signedIn(Role.REP);
		String dealId = createDeal(rep, "100");

		assertThat(patch("/api/v1/deals/" + dealId + "/stage", rep, "{\"stageCode\":\"LOST\"}")).hasStatus(422);
		assertThat(patch("/api/v1/deals/" + dealId + "/stage", rep, "{\"stageCode\":\"LOST\",\"lostReason\":\"Price\"}"))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.lostReason")
			.isEqualTo("Price");
	}

	@Test
	void staleVersionsAreRefused() {
		Session rep = signedIn(Role.REP);
		String dealId = createDeal(rep, "100");

		assertThat(patch("/api/v1/deals/" + dealId, rep, "{\"version\":99,\"name\":\"Renamed\"}")).hasStatus(409)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("version_conflict");
	}

	@Test
	void followUpsCanBeScheduledCompletedAndCounted() {
		Session rep = signedIn(Role.REP);
		String leadId = createLead(rep, uniqueEmail());
		Instant due = Instant.now().plus(1, ChronoUnit.HOURS);
		String body = "{\"title\":\"Call back\",\"type\":\"CALL\",\"dueAt\":\"" + due + "\",\"leadId\":\"" + leadId + "\"}";

		MvcTestResult created = post("/api/v1/follow-ups", rep, body);
		assertThat(created).hasStatus(201);
		String id = JsonPath.read(body(created), "$.id");

		Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
		String summary = "/api/v1/follow-ups/summary?dayStart=" + dayStart + "&dayEnd=" + dayStart.plus(1, ChronoUnit.DAYS)
				+ "&monthStart=" + dayStart.minus(30, ChronoUnit.DAYS);
		assertThat(get(summary, rep)).hasStatusOk();

		assertThat(post("/api/v1/follow-ups/" + id + "/complete", rep, "{}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("DONE");
		assertThat(post("/api/v1/follow-ups/" + id + "/complete", rep, "{}")).hasStatus(409);
		assertThat(post("/api/v1/follow-ups/" + id + "/reopen", rep, "{}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("OPEN");
	}

	@Test
	void aFollowUpNeedsSomethingToBeAbout() {
		Session rep = signedIn(Role.REP);
		String body = "{\"title\":\"Floating\",\"dueAt\":\"" + Instant.now().plusSeconds(3600) + "\"}";
		assertThat(post("/api/v1/follow-ups", rep, body)).hasStatus(422);
	}

	@Test
	void onlyTheAuthorCanChangeAnActivity() {
		Session author = signedIn(Role.ADMIN);
		Session other = signedIn(Role.ADMIN);
		String leadId = createLead(author, uniqueEmail());
		MvcTestResult logged = post("/api/v1/activities", author,
				"{\"type\":\"NOTE\",\"subject\":\"Met them\",\"body\":\"Went well\",\"leadId\":\"" + leadId + "\"}");
		assertThat(logged).hasStatus(201);
		String id = JsonPath.read(body(logged), "$.id");

		assertThat(patch("/api/v1/activities/" + id, other, "{\"subject\":\"Hacked\"}")).hasStatus(403);
		assertThat(patch("/api/v1/activities/" + id, author, "{\"subject\":\"Edited\"}")).hasStatusOk();
		assertThat(post("/api/v1/activities", author, "{\"type\":\"SYSTEM\",\"subject\":\"x\",\"leadId\":\"" + leadId + "\"}"))
			.hasStatus(422);
	}

	@Test
	void searchFindsRecordsTheUserCanSeeOnly() {
		Session alice = signedIn(Role.REP);
		Session bob = signedIn(Role.REP);
		String email = uniqueEmail();
		createLead(alice, email);

		assertThat(get("/api/v1/search?q=" + email, alice)).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(get("/api/v1/search?q=" + email, bob)).bodyJson().extractingPath("$.length()").isEqualTo(0);
	}

	@Test
	void onlyAdminsCanChangeCurrenciesAndEveryoneCanReadThem() {
		assertThat(get("/api/v1/currencies", signedIn(Role.REP))).hasStatusOk();
		assertThat(patch("/api/v1/currencies/USD", signedIn(Role.MANAGER), "{\"enabled\":true}")).hasStatus(403);
		assertThat(patch("/api/v1/currencies/INR", signedIn(Role.ADMIN), "{\"enabled\":false}")).hasStatus(409);
	}

	@Test
	void anAdminCanSetARateByHand() {
		Session admin = signedIn(Role.ADMIN);
		assertThat(put("/api/v1/exchange-rates/USD/2031-01-02", admin, "{\"rateToBase\":83.5}")).hasStatusOk();
		assertThat(put("/api/v1/exchange-rates/INR/2031-01-02", admin, "{\"rateToBase\":1}")).hasStatus(409);
		assertThat(put("/api/v1/exchange-rates/USD/2031-01-02", signedIn(Role.REP), "{\"rateToBase\":83.5}")).hasStatus(403);
	}

	@Test
	void onlyAdminsReadTheAuditLog() {
		Session admin = signedIn(Role.ADMIN);
		String leadId = createLead(admin, uniqueEmail());

		assertThat(get("/api/v1/audit?entityType=Lead&entityId=" + leadId, admin)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.items[0].action")
			.isEqualTo("CREATE");
		assertThat(get("/api/v1/audit", signedIn(Role.MANAGER))).hasStatus(403);
	}

	// ---- helpers ----

	private String createLead(Session session, String email) {
		MvcTestResult result = post("/api/v1/leads", session, leadJson(email));
		assertThat(result).hasStatus(201);
		return JsonPath.read(body(result), "$.id");
	}

	/** Makes a lead and converts it, which is how a deal normally starts. */
	private String createDeal(Session session, String amount) {
		String leadId = createLead(session, uniqueEmail());
		MvcTestResult result = post("/api/v1/leads/" + leadId + "/convert", session,
				"{\"amount\":{\"amount\":\"" + amount + "\",\"currency\":\"INR\"}}");
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.dealId");
	}

	private static String leadJson(String email) {
		return "{\"firstName\":\"Olivia\",\"lastName\":\"Martin\",\"email\":\"" + email + "\",\"companyName\":\"Apex "
				+ email + "\",\"source\":\"REFERRAL\"}";
	}

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private MvcTestResult get(String uri, Session session) {
		return this.mvc.get().uri(uri).cookie(session.accessToken()).exchange();
	}

	private MvcTestResult post(String uri, Session session, String json) {
		return send(withCsrf(this.mvc.post().uri(uri)), session, json);
	}

	private MvcTestResult patch(String uri, Session session, String json) {
		return send(withCsrf(this.mvc.patch().uri(uri)), session, json);
	}

	private MvcTestResult put(String uri, Session session, String json) {
		return send(withCsrf(this.mvc.put().uri(uri)), session, json);
	}

	private static MvcTestResult send(org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder request,
			Session session, String json) {
		return request.cookie(session.accessToken()).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
