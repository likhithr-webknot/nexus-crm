package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Tags, bulk actions and saved views: scope, all-or-nothing behaviour and filtering. */
class TagsAndBulkApiTests extends IntegrationTest {

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, Session session, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult get(String uri, Session session) {
		return this.mvc.get().uri(uri).cookie(session.accessToken()).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String field(String json, String name) {
		return json.replaceAll("^.*?\"" + name + "\":\"([^\"]+)\".*$", "$1");
	}

	private String newLead(Session session) {
		return field(json(send(this.mvc.post().uri("/api/v1/leads"), session,
				"{\"firstName\":\"Tag\",\"lastName\":\"Test\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"MANUAL\"}")), "id");
	}

	private String newTag() {
		return field(json(send(this.mvc.post().uri("/api/v1/tags"), this.admin,
				"{\"name\":\"tag-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"color\":\"brand\"}")), "id");
	}

	private String idsOf(String... ids) {
		return "[\"" + String.join("\",\"", ids) + "\"]";
	}

	@Test
	void aTagAddedToLeadsShowsOnTheListAndFiltersIt() {
		String tagged = newLead(this.admin);
		String untagged = newLead(this.admin);
		String tag = newTag();

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-tags"), this.admin,
				"{\"ids\":" + idsOf(tagged) + ",\"add\":" + idsOf(tag) + "}")).hasStatusOk();

		String filtered = json(get("/api/v1/leads?tag=" + tag, this.admin));
		assertThat(filtered).contains(tagged).doesNotContain(untagged).contains("\"color\":\"brand\"");
		assertThat(json(get("/api/v1/audit?entityType=Lead&entityId=" + tagged, this.admin))).contains("tags");
	}

	@Test
	void aBulkTagRequestWithAnUnknownRecordChangesNothing() {
		String real = newLead(this.admin);
		String tag = newTag();

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-tags"), this.admin,
				"{\"ids\":" + idsOf(real, UUID.randomUUID().toString()) + ",\"add\":" + idsOf(tag) + "}")).hasStatus(404);

		assertThat(json(get("/api/v1/leads?tag=" + tag, this.admin))).doesNotContain(real);
	}

	@Test
	void aRepCannotTagAnotherRepsLead() {
		Session rep = signedIn(Role.REP);
		String adminsLead = newLead(this.admin);
		String tag = newTag();

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-tags"), rep,
				"{\"ids\":" + idsOf(adminsLead) + ",\"add\":" + idsOf(tag) + "}")).hasStatus(404);
	}

	@Test
	void repsCannotBulkAssignLeads() {
		Session rep = signedIn(Role.REP);
		String lead = newLead(rep);

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-assign"), rep, "{\"ids\":" + idsOf(lead) + "}")).hasStatus(403);
	}

	@Test
	void aBulkAssignThatFailsHalfWayLeavesEveryLeadWhereItWas() {
		String first = newLead(this.admin);

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-assign"), this.admin,
				"{\"ids\":" + idsOf(first, UUID.randomUUID().toString()) + "}")).hasStatus(404);

		// The first lead was not made unassigned.
		assertThat(json(get("/api/v1/leads/" + first, this.admin))).contains("\"owner\":{");
	}

	@Test
	void bulkStatusMovesAllLeadsAndRefusesADisqualifyWithoutAReason() {
		String a = newLead(this.admin);
		String b = newLead(this.admin);

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-status"), this.admin,
				"{\"ids\":" + idsOf(a, b) + ",\"status\":\"CONTACTED\"}")).hasStatusOk();
		assertThat(json(get("/api/v1/leads/" + a, this.admin))).contains("\"status\":\"CONTACTED\"");
		assertThat(json(get("/api/v1/leads/" + b, this.admin))).contains("\"status\":\"CONTACTED\"");

		assertThat(send(this.mvc.post().uri("/api/v1/leads/bulk-status"), this.admin,
				"{\"ids\":" + idsOf(a, b) + ",\"status\":\"DISQUALIFIED\"}")).hasStatus(422);
		assertThat(json(get("/api/v1/leads/" + a, this.admin))).contains("\"status\":\"CONTACTED\"");
	}

	@Test
	void deletingATagTakesItOffTheRecords() {
		String lead = newLead(this.admin);
		String tag = newTag();
		send(this.mvc.post().uri("/api/v1/leads/bulk-tags"), this.admin, "{\"ids\":" + idsOf(lead) + ",\"add\":" + idsOf(tag) + "}");

		assertThat(send(this.mvc.delete().uri("/api/v1/tags/" + tag), this.admin, null)).hasStatus(204);

		assertThat(json(get("/api/v1/leads/" + lead, this.admin))).contains("\"tags\":[]");
	}

	@Test
	void repsCannotDeleteTagsButCanCreateThem() {
		Session rep = signedIn(Role.REP);
		String tag = newTag();

		assertThat(send(this.mvc.delete().uri("/api/v1/tags/" + tag), rep, null)).hasStatus(403);
		assertThat(send(this.mvc.post().uri("/api/v1/tags"), rep, "{\"name\":\"rep-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
			.hasStatus(201);
	}

	@Test
	void aSharedViewIsSeenByOthersButAPrivateOneIsNot() {
		Session rep = signedIn(Role.REP);
		String shared = field(json(send(this.mvc.post().uri("/api/v1/saved-views"), this.admin,
				"{\"entityType\":\"lead\",\"name\":\"Shared " + UUID.randomUUID() + "\",\"shared\":true,\"filters\":{\"status\":\"QUALIFIED\"}}")), "id");
		String hidden = field(json(send(this.mvc.post().uri("/api/v1/saved-views"), this.admin,
				"{\"entityType\":\"lead\",\"name\":\"Private " + UUID.randomUUID() + "\",\"filters\":{}}")), "id");

		String seen = json(get("/api/v1/saved-views?entityType=lead", rep));
		assertThat(seen).contains(shared).doesNotContain(hidden);
		// The rep may not change or delete a view that is not theirs.
		assertThat(send(this.mvc.delete().uri("/api/v1/saved-views/" + shared), rep, null)).hasStatus(404);
	}

	@Test
	void aViewCannotStoreAFilterThePageDoesNotHave() {
		assertThat(send(this.mvc.post().uri("/api/v1/saved-views"), this.admin,
				"{\"entityType\":\"contact\",\"name\":\"Odd\",\"filters\":{\"status\":\"NEW\"}}")).hasStatus(422);
	}

}
