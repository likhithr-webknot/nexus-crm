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

/**
 * Contacts are a phone book. Once someone is a contact, they stay one, whatever happens to the lead they came from
 * or to the deals they are part of: a lost deal, a deleted deal, a deleted lead.
 */
class ContactPhoneBookTests extends IntegrationTest {

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, String body) {
		var builder = withCsrf(request).cookie(this.admin.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String field(String json, String name) {
		return json.replaceAll("^.*?\"" + name + "\":\"([^\"]+)\".*$", "$1");
	}

	@Test
	void aContactSurvivesALostDealAndADeletedDealAndADeletedLead() {
		String email = uniqueEmail();
		MvcTestResult lead = send(this.mvc.post().uri("/api/v1/leads"),
				"{\"firstName\":\"Priya\",\"lastName\":\"Nair\",\"email\":\"" + email + "\",\"companyName\":\"Solace Energy\",\"source\":\"REFERRAL\"}");
		String leadId = field(json(lead), "id");

		String converted = json(send(this.mvc.post().uri("/api/v1/leads/" + leadId + "/convert"), "{}"));
		String contactId = field(converted, "contactId");
		String dealId = field(converted, "dealId");
		assertThat(this.mvc.get().uri("/api/v1/contacts/" + contactId).cookie(this.admin.accessToken())).hasStatusOk();

		// The deal is lost: the contact is still in the phone book, and the lost deal is still on their record.
		assertThat(send(this.mvc.patch().uri("/api/v1/deals/" + dealId + "/stage"), "{\"stageCode\":\"LOST\",\"lostReason\":\"Went with a competitor\"}"))
			.hasStatusOk();
		assertThat(this.mvc.get().uri("/api/v1/contacts/" + contactId).cookie(this.admin.accessToken())).hasStatusOk();
		assertThat(json(this.mvc.get().uri("/api/v1/deals?contactId=" + contactId).cookie(this.admin.accessToken()).exchange()))
			.contains(dealId)
			.contains("\"stageCode\":\"LOST\"");

		// The deal is deleted: still there.
		assertThat(send(this.mvc.delete().uri("/api/v1/deals/" + dealId), null)).hasStatus(204);
		assertThat(this.mvc.get().uri("/api/v1/contacts/" + contactId).cookie(this.admin.accessToken())).hasStatusOk();

		// The lead is deleted: still there, and still found by searching for them.
		assertThat(send(this.mvc.delete().uri("/api/v1/leads/" + leadId), null)).hasStatus(204);
		assertThat(this.mvc.get().uri("/api/v1/contacts/" + contactId).cookie(this.admin.accessToken())).hasStatusOk();
		assertThat(json(this.mvc.get().uri("/api/v1/contacts?q={q}", "Priya").cookie(this.admin.accessToken()).exchange())).contains(contactId);
	}

	@Test
	void aDisqualifiedLeadIsNotAContactBecauseItNeverBecameACustomer() {
		MvcTestResult lead = send(this.mvc.post().uri("/api/v1/leads"),
				"{\"firstName\":\"Not\",\"lastName\":\"Interested\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"WEBSITE\"}");
		String leadId = field(json(lead), "id");

		assertThat(send(this.mvc.post().uri("/api/v1/leads/" + leadId + "/status"), "{\"status\":\"DISQUALIFIED\",\"reason\":\"No budget\"}")).hasStatusOk();

		assertThat(json(this.mvc.get().uri("/api/v1/contacts?q={q}", "Interested").cookie(this.admin.accessToken()).exchange())).contains("\"total\":0");
	}

	@Test
	void convertingTwoLeadsFromTheSameCompanyKeepsOneCompanyAndOneContactPerPerson() {
		String email = uniqueEmail();
		String company = "Shared Co " + java.util.UUID.randomUUID();
		for (int i = 0; i < 2; i++) {
			String body = "{\"firstName\":\"Sam\",\"lastName\":\"Same\",\"email\":\"" + (i == 0 ? email : uniqueEmail()) + "\",\"companyName\":\"" + company + "\",\"source\":\"MANUAL\"}";
			String leadId = field(json(send(this.mvc.post().uri("/api/v1/leads"), body)), "id");
			assertThat(send(this.mvc.post().uri("/api/v1/leads/" + leadId + "/convert"), "{}")).hasStatusOk();
		}

		assertThat(json(this.mvc.get().uri("/api/v1/companies?q={q}", company).cookie(this.admin.accessToken()).exchange())).contains("\"total\":1");
	}

}
