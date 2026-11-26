package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Editing a lead works the way the edit dialog sends it, for each role, and a stale edit is refused. */
class LeadUpdateTests extends IntegrationTest {

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, Session session, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String createLead(Session session) {
		MvcTestResult created = send(this.mvc.post().uri("/api/v1/leads"), session,
				"{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"WEBSITE\"}");
		assertThat(created).hasStatus(201);
		return json(created).replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void theEditDialogPayloadIsAccepted() {
		String id = createLead(this.admin);
		String email = uniqueEmail();

		// The same fields, in the same shape, as the edit dialog sends: an empty phone, a value and a currency, and the version.
		MvcTestResult result = send(this.mvc.patch().uri("/api/v1/leads/" + id), this.admin,
				"{\"firstName\":\"Ada\",\"lastName\":\"King\",\"email\":\"" + email + "\",\"phone\":\"\",\"companyName\":\"Analytical Engines\","
						+ "\"source\":\"REFERRAL\",\"estimatedValue\":{\"amount\":\"2500.50\",\"currency\":\"USD\"},\"version\":0}");

		assertThat(result).hasStatusOk();
		assertThat(json(result)).contains("\"lastName\":\"King\"").contains("Analytical Engines").contains("\"2500.50\"").contains("REFERRAL");
	}

	@Test
	void aRepCanEditALeadTheyOwn() {
		Session rep = signedIn(Role.REP);
		String id = createLead(rep);

		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + id), rep, "{\"companyName\":\"Mine\",\"version\":0}")).hasStatusOk();
	}

	@Test
	void editingTwiceNeedsTheNewVersion() {
		String id = createLead(this.admin);

		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + id), this.admin, "{\"companyName\":\"First\",\"version\":0}")).hasStatusOk();
		// The second edit still carries the old version, so it is refused instead of overwriting the first.
		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + id), this.admin, "{\"companyName\":\"Second\",\"version\":0}")).hasStatus(409);
		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + id), this.admin, "{\"companyName\":\"Second\",\"version\":1}")).hasStatusOk();
	}

	private String idOf(MvcTestResult result) {
		return json(result).replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1");
	}

	private String createCompany(String name) {
		MvcTestResult created = send(this.mvc.post().uri("/api/v1/companies"), this.admin, "{\"name\":\"" + name + "\"}");
		assertThat(created).hasStatus(201);
		return idOf(created);
	}

	@Test
	void aLeadCanBeLinkedToAnExistingCompanyAndUnlinked() {
		String leadId = createLead(this.admin);
		String name = "Linked " + uniqueEmail();
		String companyId = createCompany(name);

		MvcTestResult linked = send(this.mvc.patch().uri("/api/v1/leads/" + leadId), this.admin,
				"{\"companyId\":\"" + companyId + "\",\"version\":0}");
		assertThat(linked).hasStatusOk();
		assertThat(json(linked)).contains("\"company\":{\"id\":\"" + companyId + "\"").contains("\"companyName\":\"" + name + "\"");

		// An empty company name is how the edit dialog says "no company".
		MvcTestResult unlinked = send(this.mvc.patch().uri("/api/v1/leads/" + leadId), this.admin,
				"{\"companyName\":\"\",\"version\":1}");
		assertThat(unlinked).hasStatusOk();
		assertThat(json(unlinked)).contains("\"company\":null");
	}

	@Test
	void aCompanyNameFindsTheExistingCompanyInsteadOfDuplicatingIt() {
		String name = "Reused " + uniqueEmail();
		String companyId = createCompany(name);

		MvcTestResult created = send(this.mvc.post().uri("/api/v1/leads"), this.admin,
				"{\"firstName\":\"Ada\",\"email\":\"" + uniqueEmail() + "\",\"companyName\":\"" + name.toUpperCase() + "\"}");
		assertThat(created).hasStatus(201);
		assertThat(json(created)).contains("\"company\":{\"id\":\"" + companyId + "\"");
	}

	@Test
	void renamingACompanyReachesItsLeads() {
		String leadId = createLead(this.admin);
		String companyId = createCompany("Before " + uniqueEmail());
		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + leadId), this.admin,
				"{\"companyId\":\"" + companyId + "\",\"version\":0}")).hasStatusOk();

		assertThat(send(this.mvc.patch().uri("/api/v1/companies/" + companyId), this.admin, "{\"name\":\"Renamed Co\",\"version\":0}"))
			.hasStatusOk();

		assertThat(json(send(this.mvc.get().uri("/api/v1/leads/" + leadId), this.admin, null))).contains("\"companyName\":\"Renamed Co\"");
	}

	@Test
	void aLeadCannotBeLinkedToAnUnknownCompany() {
		String leadId = createLead(this.admin);

		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + leadId), this.admin,
				"{\"companyId\":\"" + java.util.UUID.randomUUID() + "\",\"version\":0}")).hasStatus(404);
	}

	@Test
	void anInvalidEmailIsReportedNotSwallowed() {
		String id = createLead(this.admin);

		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + id), this.admin, "{\"email\":\"not-an-email\"}")).hasStatus(422);
	}

}
