package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The fixes from the security review that need the whole application running. */
class SecurityHardeningTests extends IntegrationTest {

	@Autowired
	private JdbcClient jdbc;

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(Session session, MockMvcRequestBuilder request, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult get(Session session, String uri) {
		return this.mvc.get().uri(uri).cookie(session.accessToken()).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String id(MvcTestResult result) {
		return json(result).replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
	}

	// --- sessions

	@Test
	void anAccessTokenStopsWorkingTheMomentItsSessionIsSignedOut() {
		String email = uniqueEmail();
		invite(email, Role.REP);
		String google = uniqueGoogleId();
		Session first = signIn(google, email);
		Session second = signIn(google, email);
		assertThat(get(first, "/api/v1/me")).hasStatusOk();
		assertThat(get(second, "/api/v1/me")).hasStatusOk();

		assertThat(send(first, this.mvc.post().uri("/api/v1/auth/logout"), null)).hasStatus(204);

		assertThat(get(first, "/api/v1/me")).hasStatus(401);
		// Another browser of the same person is a different session and stays signed in.
		assertThat(get(second, "/api/v1/me")).hasStatusOk();
	}

	@Test
	void signingOutEverywhereEndsEverySession() {
		String email = uniqueEmail();
		invite(email, Role.REP);
		String google = uniqueGoogleId();
		Session first = signIn(google, email);
		Session second = signIn(google, email);

		assertThat(send(first, this.mvc.post().uri("/api/v1/auth/logout-all"), null)).hasStatus(204);

		assertThat(get(first, "/api/v1/me")).hasStatus(401);
		assertThat(get(second, "/api/v1/me")).hasStatus(401);
	}

	// --- request size

	@Test
	void anOversizedJsonBodyIsRefusedWith413() {
		String big = "{\"name\":\"" + "x".repeat(1_100_000) + "\"}";

		assertThat(send(this.admin, this.mvc.post().uri("/api/v1/workspaces/quotes"), big)).hasStatus(413);
	}

	@Test
	void theSignInAndRefreshEndpointsAreGuardedToo() {
		String big = "{\"x\":\"" + "x".repeat(1_100_000) + "\"}";

		assertThat(this.mvc.post().uri("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(big).exchange())
			.hasStatus(413);
	}

	// --- user list

	@Test
	void staffSeeOnlyActivePeopleAndNoSignInActivityButAdminsSeeEverything() {
		String invitedEmail = uniqueEmail();
		send(this.admin, this.mvc.post().uri("/api/v1/users"),
				"{\"email\":\"" + invitedEmail + "\",\"displayName\":\"Pending Person\",\"role\":\"REP\"}");
		Session rep = signedIn(Role.REP);

		String forRep = json(get(rep, "/api/v1/users"));
		String forAdmin = json(get(this.admin, "/api/v1/users"));

		assertThat(forAdmin).contains(invitedEmail);
		assertThat(forRep).doesNotContain(invitedEmail).doesNotContain("INVITED").doesNotContain("DISABLED");
		assertThat(forRep).doesNotContainPattern("\"lastLoginAt\":\"");
		assertThat(forAdmin).containsPattern("\"lastLoginAt\":\"");
	}

	// --- AI

	@Test
	void researchOnlyFillsInValuesFromTheCompanysOwnSite() {
		String domain = "acme-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
		String companyId = id(send(this.admin, this.mvc.post().uri("/api/v1/companies"),
				"{\"name\":\"Research Co " + UUID.randomUUID() + "\",\"domain\":\"" + domain + "\"}"));

		research("company", companyId, "https://" + domain, "https://tracker.evil.example/pixel.png", null, "https://" + domain + "/about");
		assertThat(send(this.admin, this.mvc.post().uri("/api/v1/ai/research/company/" + companyId + "/apply"), "{\"field\":\"logo\"}"))
			.hasStatus(422);

		research("company", companyId, "https://" + domain, "https://cdn." + domain + "/logo.png", null, "https://" + domain + "/about");
		assertThat(send(this.admin, this.mvc.post().uri("/api/v1/ai/research/company/" + companyId + "/apply"), "{\"field\":\"logo\"}"))
			.hasStatus(204);

		research("company", companyId, "https://other.example", null, null, "https://other.example");
		assertThat(send(this.admin, this.mvc.post().uri("/api/v1/ai/research/company/" + companyId + "/apply"), "{\"field\":\"website\"}"))
			.hasStatus(422);
	}

	@Test
	void aResearchedEmailMustBeOnTheCompanysDomain() {
		String domain = "mail-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
		String companyId = id(send(this.admin, this.mvc.post().uri("/api/v1/companies"),
				"{\"name\":\"Mail Co " + UUID.randomUUID() + "\",\"domain\":\"" + domain + "\"}"));
		String contactId = id(send(this.admin, this.mvc.post().uri("/api/v1/contacts"),
				"{\"firstName\":\"Pat\",\"lastName\":\"Lee\",\"companyId\":\"" + companyId + "\"}"));

		research("contact", contactId, null, null, "pat@attacker.example", "https://" + domain + "/team");
		assertThat(send(this.admin, this.mvc.post().uri("/api/v1/ai/research/contact/" + contactId + "/apply"), "{\"field\":\"email\"}"))
			.hasStatus(422);

		research("contact", contactId, null, null, "pat@" + domain, "https://" + domain + "/team");
		assertThat(send(this.admin, this.mvc.post().uri("/api/v1/ai/research/contact/" + contactId + "/apply"), "{\"field\":\"email\"}"))
			.hasStatus(204);
	}

	private void research(String kind, String recordId, String website, String logo, String email, String source) {
		String result = "{\"website\":" + quote(website) + ",\"logoUrl\":" + quote(logo) + ",\"email\":" + quote(email)
				+ ",\"summary\":null,\"sources\":[\"" + source + "\"],\"researchedAt\":\"" + Instant.now() + "\"}";
		this.jdbc.sql("insert into ai_research (kind, record_id, result) values (:kind, :id, cast(:result as jsonb)) "
				+ "on conflict (kind, record_id) do update set result = excluded.result")
			.param("kind", kind).param("id", UUID.fromString(recordId)).param("result", result).update();
	}

	private static String quote(String value) {
		return (value == null) ? "null" : "\"" + value + "\"";
	}

}
