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

/** The recycle bin: who can use it, what it shows, and what a restore refuses. */
class TrashApiTests extends IntegrationTest {

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

	private String newLead(String email) {
		return field(json(send(this.mvc.post().uri("/api/v1/leads"), this.admin,
				"{\"firstName\":\"Trash\",\"lastName\":\"Test\",\"email\":\"" + email + "\",\"source\":\"MANUAL\"}")), "id");
	}

	@Test
	void repsCannotSeeOrRestoreAnything() {
		Session rep = signedIn(Role.REP);

		assertThat(get("/api/v1/trash", rep)).hasStatus(403);
		assertThat(send(this.mvc.post().uri("/api/v1/trash/LEAD/" + UUID.randomUUID() + "/restore"), rep, null)).hasStatus(403);
	}

	@Test
	void aDeletedLeadAppearsInTheBinAndComesBackWithAnAuditEntry() {
		String id = newLead(uniqueEmail());
		assertThat(send(this.mvc.delete().uri("/api/v1/leads/" + id), this.admin, null)).hasStatus(204);
		assertThat(get("/api/v1/leads/" + id, this.admin)).hasStatus(404);

		assertThat(json(get("/api/v1/trash?type=LEAD", this.admin))).contains(id).contains("\"name\":\"Trash Test\"");

		assertThat(send(this.mvc.post().uri("/api/v1/trash/LEAD/" + id + "/restore"), this.admin, null)).hasStatus(204);
		assertThat(get("/api/v1/leads/" + id, this.admin)).hasStatusOk();
		assertThat(json(get("/api/v1/trash?type=LEAD", this.admin))).doesNotContain(id);
		assertThat(json(get("/api/v1/audit?entityType=Lead&entityId=" + id, this.admin))).contains("RESTORE");
	}

	@Test
	void aRestoreThatWouldDuplicateAnOpenLeadIsRefused() {
		String email = uniqueEmail();
		String first = newLead(email);
		send(this.mvc.delete().uri("/api/v1/leads/" + first), this.admin, null);
		// Someone creates a new open lead with the same email while the first one is in the bin.
		newLead(email);

		assertThat(send(this.mvc.post().uri("/api/v1/trash/LEAD/" + first + "/restore"), this.admin, null)).hasStatus(409);
		// It stays in the bin, nothing was half-done.
		assertThat(json(get("/api/v1/trash?type=LEAD", this.admin))).contains(first);
	}

	@Test
	void aDealCannotComeBackWhileItsCompanyIsStillDeleted() {
		String companyId = field(json(send(this.mvc.post().uri("/api/v1/companies"), this.admin, "{\"name\":\"Bin Co " + UUID.randomUUID() + "\"}")), "id");
		String dealId = field(json(send(this.mvc.post().uri("/api/v1/deals"), this.admin,
				"{\"name\":\"Bin deal\",\"companyId\":\"" + companyId + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}")), "id");
		send(this.mvc.delete().uri("/api/v1/deals/" + dealId), this.admin, null);
		assertThat(send(this.mvc.delete().uri("/api/v1/companies/" + companyId), this.admin, null)).hasStatus(204);

		assertThat(send(this.mvc.post().uri("/api/v1/trash/DEAL/" + dealId + "/restore"), this.admin, null)).hasStatus(409);
		assertThat(send(this.mvc.post().uri("/api/v1/trash/COMPANY/" + companyId + "/restore"), this.admin, null)).hasStatus(204);
		assertThat(send(this.mvc.post().uri("/api/v1/trash/DEAL/" + dealId + "/restore"), this.admin, null)).hasStatus(204);
		assertThat(get("/api/v1/deals/" + dealId, this.admin)).hasStatusOk();
	}

	@Test
	void aManagerOnlySeesAndRestoresRecordsInTheirOwnScope() {
		// A lead owned by the admin, who is not in the manager's team.
		String id = field(json(send(this.mvc.post().uri("/api/v1/leads"), this.admin,
				"{\"firstName\":\"Other\",\"lastName\":\"Team\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"MANUAL\"}")), "id");
		send(this.mvc.delete().uri("/api/v1/leads/" + id), this.admin, null);
		Session manager = signedIn(Role.MANAGER);

		assertThat(json(get("/api/v1/trash", manager))).doesNotContain(id);
		// Same answer as for a record that does not exist, so nothing is revealed.
		assertThat(send(this.mvc.post().uri("/api/v1/trash/LEAD/" + id + "/restore"), manager, null)).hasStatus(404);
	}

	@Test
	void restoringSomethingThatIsNotDeletedIsNotFound() {
		String id = newLead(uniqueEmail());

		assertThat(send(this.mvc.post().uri("/api/v1/trash/LEAD/" + id + "/restore"), this.admin, null)).hasStatus(404);
	}

}
