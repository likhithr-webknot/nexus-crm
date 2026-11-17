package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Every business record can be created, read, changed and deleted, and a deleted record is gone. */
class CrudCycleTests extends IntegrationTest {

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, String body) {
		var builder = withCsrf(request).cookie(this.admin.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult get(String uri) {
		return this.mvc.get().uri(uri).cookie(this.admin.accessToken()).exchange();
	}

	private String id(MvcTestResult result) {
		String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		return json.replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1");
	}

	private String newContact(String company) {
		return id(send(this.mvc.post().uri("/api/v1/contacts"),
				"{\"firstName\":\"Test\",\"lastName\":\"Contact\",\"email\":\"" + uniqueEmail() + "\",\"companyName\":\"" + company + "\"}"));
	}

	@Test
	void aLeadIsCreatedChangedAndDeleted() {
		String id = id(send(this.mvc.post().uri("/api/v1/leads"),
				"{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"WEBSITE\"}"));

		assertThat(send(this.mvc.patch().uri("/api/v1/leads/" + id), "{\"companyName\":\"Analytical Engines\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.companyName").isEqualTo("Analytical Engines");
		assertThat(get("/api/v1/leads/" + id)).hasStatusOk();

		assertThat(send(this.mvc.delete().uri("/api/v1/leads/" + id), null)).hasStatus(204);
		assertThat(get("/api/v1/leads/" + id)).hasStatus(404);
		assertThat(send(this.mvc.delete().uri("/api/v1/leads/" + id), null)).hasStatus(404);
	}

	@Test
	void aContactIsCreatedChangedAndDeleted() {
		String id = newContact("Contact Co " + UUID.randomUUID());

		assertThat(send(this.mvc.patch().uri("/api/v1/contacts/" + id), "{\"jobTitle\":\"CTO\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.jobTitle").isEqualTo("CTO");

		assertThat(send(this.mvc.delete().uri("/api/v1/contacts/" + id), null)).hasStatus(204);
		assertThat(get("/api/v1/contacts/" + id)).hasStatus(404);
	}

	@Test
	void aCompanyIsCreatedChangedAndDeletedUnlessSomethingStillUsesIt() {
		String id = id(send(this.mvc.post().uri("/api/v1/companies"), "{\"name\":\"Acme " + UUID.randomUUID() + "\"}"));
		assertThat(send(this.mvc.patch().uri("/api/v1/companies/" + id), "{\"industry\":\"Software\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.industry").isEqualTo("Software");
		assertThat(send(this.mvc.delete().uri("/api/v1/companies/" + id), null)).hasStatus(204);
		assertThat(get("/api/v1/companies/" + id)).hasStatus(404);

		// A company that still has a contact cannot be deleted.
		String name = "Busy Co " + UUID.randomUUID();
		String contactId = newContact(name);
		var company = this.mvc.get().uri("/api/v1/companies?q={q}", name).cookie(this.admin.accessToken()).exchange();
		String busyId = new String(company.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
			.replaceAll(".*?\"items\":\\[\\{\"id\":\"([^\"]+)\".*", "$1");
		assertThat(send(this.mvc.delete().uri("/api/v1/companies/" + busyId), null)).hasStatus(409);
		assertThat(send(this.mvc.delete().uri("/api/v1/contacts/" + contactId), null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/companies/" + busyId), null)).hasStatus(204);
	}

	@Test
	void aDealIsCreatedChangedMovedAndDeleted() {
		String companyId = id(send(this.mvc.post().uri("/api/v1/companies"), "{\"name\":\"Deal Co " + UUID.randomUUID() + "\"}"));
		String id = id(send(this.mvc.post().uri("/api/v1/deals"),
				"{\"name\":\"Big deal\",\"companyId\":\"" + companyId + "\",\"amount\":{\"amount\":\"1000\",\"currency\":\"INR\"}}"));

		assertThat(send(this.mvc.patch().uri("/api/v1/deals/" + id), "{\"name\":\"Bigger deal\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.name").isEqualTo("Bigger deal");
		assertThat(send(this.mvc.patch().uri("/api/v1/deals/" + id + "/stage"), "{\"stageCode\":\"IN_PROGRESS\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.stageCode").isEqualTo("IN_PROGRESS");

		assertThat(send(this.mvc.delete().uri("/api/v1/deals/" + id), null)).hasStatus(204);
		assertThat(get("/api/v1/deals/" + id)).hasStatus(404);
	}

	@Test
	void anActivityIsLoggedChangedAndDeleted() {
		String contactId = newContact("Activity Co " + UUID.randomUUID());
		String id = id(send(this.mvc.post().uri("/api/v1/activities"),
				"{\"type\":\"NOTE\",\"subject\":\"First\",\"contactId\":\"" + contactId + "\"}"));

		assertThat(send(this.mvc.patch().uri("/api/v1/activities/" + id), "{\"subject\":\"Edited\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.subject").isEqualTo("Edited");
		assertThat(send(this.mvc.delete().uri("/api/v1/activities/" + id), null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/activities/" + id), null)).hasStatus(404);
	}

	@Test
	void aFollowUpIsCreatedChangedAndDeleted() {
		String contactId = newContact("Follow Co " + UUID.randomUUID());
		String id = id(send(this.mvc.post().uri("/api/v1/follow-ups"),
				"{\"title\":\"Call back\",\"type\":\"CALL\",\"dueAt\":\"" + Instant.now().plusSeconds(3600) + "\",\"contactId\":\"" + contactId + "\"}"));

		assertThat(send(this.mvc.patch().uri("/api/v1/follow-ups/" + id), "{\"title\":\"Call back soon\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.title").isEqualTo("Call back soon");
		assertThat(send(this.mvc.delete().uri("/api/v1/follow-ups/" + id), null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/follow-ups/" + id), null)).hasStatus(404);
	}

	@Test
	void aRevenueTargetIsSetAndRemoved() {
		assertThat(send(this.mvc.put().uri("/api/v1/revenue-targets/2035-07"), "{\"amount\":\"100000\"}")).hasStatusOk();
		assertThat(send(this.mvc.delete().uri("/api/v1/revenue-targets/2035-07"), null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/revenue-targets/2035-07"), null)).hasStatus(404);
	}

}
