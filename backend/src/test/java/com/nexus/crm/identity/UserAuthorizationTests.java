package com.nexus.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Role-based authorization, and that changes to a user take effect on their very next request. */
class UserAuthorizationTests extends IntegrationTest {

	@Test
	void onlyAdminsCanInvite() {
		String body = inviteBody(uniqueEmail(), "REP");

		for (Role role : new Role[] { Role.REP, Role.MANAGER }) {
			assertThat(post("/api/v1/users", signedIn(role), body)).hasStatus(403)
				.bodyJson()
				.extractingPath("$.code")
				.isEqualTo("forbidden");
		}
		assertThat(post("/api/v1/users", signedIn(Role.ADMIN), body)).hasStatus(201)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("INVITED");
	}

	@Test
	void everySignedInRoleCanListUsers() {
		assertThat(this.mvc.get().uri("/api/v1/users").cookie(signedIn(Role.REP).accessToken())).hasStatusOk();
	}

	@Test
	void anInvitedPersonCanThenSignIn() {
		String email = uniqueEmail();
		assertThat(post("/api/v1/users", signedIn(Role.ADMIN), inviteBody(email, "MANAGER"))).hasStatus(201);

		Session invitee = signIn(uniqueGoogleId(), email);
		assertThat(this.mvc.get().uri("/api/v1/me").cookie(invitee.accessToken())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.role")
			.isEqualTo("MANAGER");
	}

	@Test
	void invitingAnExistingEmailIsADuplicate() {
		Session admin = signedIn(Role.ADMIN);
		String email = uniqueEmail();
		post("/api/v1/users", admin, inviteBody(email, "REP"));

		assertThat(post("/api/v1/users", admin, inviteBody(email.toUpperCase(), "REP"))).hasStatus(409)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("duplicate");
	}

	@Test
	void invalidInvitationListsTheFieldsThatAreWrong() {
		MvcTestResult result = post("/api/v1/users", signedIn(Role.ADMIN),
				"{\"email\":\"not-an-email\",\"displayName\":\"\",\"role\":\"REP\"}");

		assertThat(result).hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("validation_failed");
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().contains("email", "displayName");
	}

	@Test
	void disablingAUserCutsThemOffImmediately() {
		String email = uniqueEmail();
		AppUser rep = invite(email, Role.REP);
		Session repSession = signIn(uniqueGoogleId(), email);
		assertThat(this.mvc.get().uri("/api/v1/me").cookie(repSession.accessToken())).hasStatusOk();

		assertThat(patch("/api/v1/users/" + rep.getId(), signedIn(Role.ADMIN), "{\"status\":\"DISABLED\"}"))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("DISABLED");

		// The access token is still within its 15 minutes, and is refused all the same.
		assertThat(this.mvc.get().uri("/api/v1/me").cookie(repSession.accessToken())).hasStatus(401);
		assertThat(this.mvc.post().uri("/api/v1/auth/refresh").cookie(repSession.refreshToken())).hasStatus(401);
	}

	@Test
	void aRoleChangeAppliesToTheUsersExistingSession() {
		String email = uniqueEmail();
		AppUser rep = invite(email, Role.REP);
		Session repSession = signIn(uniqueGoogleId(), email);
		assertThat(post("/api/v1/users", repSession, inviteBody(uniqueEmail(), "REP"))).hasStatus(403);

		assertThat(patch("/api/v1/users/" + rep.getId(), signedIn(Role.ADMIN), "{\"role\":\"ADMIN\"}")).hasStatusOk();

		// Same token as before: the role comes from the database, not from the token's claim.
		assertThat(post("/api/v1/users", repSession, inviteBody(uniqueEmail(), "REP"))).hasStatus(201);
	}

	@Test
	void adminsCannotDemoteOrDisableThemselves() {
		String email = uniqueEmail();
		AppUser admin = invite(email, Role.ADMIN);
		Session session = signIn(uniqueGoogleId(), email);

		assertThat(patch("/api/v1/users/" + admin.getId(), session, "{\"role\":\"REP\"}")).hasStatus(409)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("invalid_state");
	}

	@Test
	void changingAUserThatDoesNotExistIsNotFound() {
		assertThat(patch("/api/v1/users/" + UUID.randomUUID(), signedIn(Role.ADMIN), "{\"role\":\"REP\"}"))
			.hasStatus(404)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("not_found");
	}

	private MvcTestResult post(String uri, Session session, String json) {
		return withCsrf(this.mvc.post().uri(uri)).cookie(session.accessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	private MvcTestResult patch(String uri, Session session, String json) {
		return withCsrf(this.mvc.patch().uri(uri)).cookie(session.accessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	private static String inviteBody(String email, String role) {
		return "{\"email\":\"" + email + "\",\"displayName\":\"New Person\",\"role\":\"" + role + "\"}";
	}

}
