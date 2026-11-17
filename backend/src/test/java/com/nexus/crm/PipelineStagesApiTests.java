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

/** The pipeline is Qualified, In progress, Won and Lost. */
class PipelineStagesApiTests extends IntegrationTest {

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, String body) {
		var builder = withCsrf(request).cookie(this.admin.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static String text(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String newDeal() {
		String company = text(send(this.mvc.post().uri("/api/v1/companies"), "{\"name\":\"Stage Co " + UUID.randomUUID() + "\"}"))
			.replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
		return text(send(this.mvc.post().uri("/api/v1/deals"),
				"{\"name\":\"Stage deal\",\"companyId\":\"" + company + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}"))
			.replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
	}

	private MvcTestResult move(String dealId, String stage) {
		return send(this.mvc.patch().uri("/api/v1/deals/" + dealId + "/stage"), "{\"stageCode\":\"" + stage + "\"}");
	}

	@Test
	void theBoardHasFourStagesInOrder() {
		String stages = text(this.mvc.get().uri("/api/v1/pipeline/stages").cookie(this.admin.accessToken()).exchange());

		assertThat(stages).containsSubsequence("\"code\":\"QUALIFIED\"", "\"code\":\"IN_PROGRESS\"", "\"code\":\"WON\"", "\"code\":\"LOST\"");
		assertThat(stages).contains("\"name\":\"In progress\"").doesNotContain("DEMO").doesNotContain("PROPOSAL").doesNotContain("NEGOTIATION");
		assertThat(stages.split("\"code\"", -1)).hasSize(5);
	}

	@Test
	void aDealMovesFromQualifiedThroughInProgressToWon() {
		String deal = newDeal();

		assertThat(text(move(deal, "IN_PROGRESS"))).contains("\"stageCode\":\"IN_PROGRESS\"");
		assertThat(text(move(deal, "WON"))).contains("\"stageCode\":\"WON\"");
	}

	@Test
	void theRetiredStagesAreGone() {
		String deal = newDeal();

		for (String old : new String[] { "DEMO", "PROPOSAL", "NEGOTIATION" }) {
			assertThat(move(deal, old)).hasStatus(422);
		}
	}

	@Test
	void aSavedViewMayFilterOnInProgress() {
		assertThat(send(this.mvc.post().uri("/api/v1/saved-views"),
				"{\"entityType\":\"deal\",\"name\":\"Open work " + UUID.randomUUID() + "\",\"filters\":{\"stage\":\"IN_PROGRESS\"}}"))
			.hasStatus(201);
	}

}
