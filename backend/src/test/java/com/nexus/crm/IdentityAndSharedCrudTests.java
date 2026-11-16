package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Create, read, update and delete for teams, users, currencies and exchange rates. */
class IdentityAndSharedCrudTests extends IntegrationTest {

	private MvcTestResult send(org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder request,
			Session session, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String id(MvcTestResult result) {
		String json = new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
		return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void anAdminRunsATeamThroughItsWholeLife() {
		Session admin = signedIn(Role.ADMIN);
		String name = "Team " + UUID.randomUUID();

		MvcTestResult created = send(this.mvc.post().uri("/api/v1/teams"), admin, "{\"name\":\"" + name + "\"}");
		assertThat(created).hasStatus(201);
		String teamId = id(created);

		// A second team with the same name is refused.
		assertThat(send(this.mvc.post().uri("/api/v1/teams"), admin, "{\"name\":\"" + name + "\"}")).hasStatus(409);

		MvcTestResult renamed = send(this.mvc.patch().uri("/api/v1/teams/" + teamId), admin, "{\"name\":\"" + name + " renamed\"}");
		assertThat(renamed).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo(name + " renamed");

		// A member stops the team being deleted. Once they leave, it can be.
		AppUser member = invite(uniqueEmail(), Role.REP);
		assertThat(send(this.mvc.put().uri("/api/v1/users/" + member.getId() + "/team"), admin, "{\"teamId\":\"" + teamId + "\"}"))
			.hasStatusOk();
		assertThat(send(this.mvc.delete().uri("/api/v1/teams/" + teamId), admin, null)).hasStatus(409)
			.bodyJson().extractingPath("$.code").isEqualTo("invalid_state");
		assertThat(send(this.mvc.put().uri("/api/v1/users/" + member.getId() + "/team"), admin, "{\"teamId\":null}")).hasStatusOk();
		assertThat(send(this.mvc.delete().uri("/api/v1/teams/" + teamId), admin, null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/teams/" + teamId), admin, null)).hasStatus(404);
	}

	@Test
	void aRepCannotChangeTeams() {
		Session rep = signedIn(Role.REP);
		assertThat(send(this.mvc.post().uri("/api/v1/teams"), rep, "{\"name\":\"Nope\"}")).hasStatus(403);
		assertThat(send(this.mvc.delete().uri("/api/v1/teams/" + UUID.randomUUID()), rep, null)).hasStatus(403);
		assertThat(this.mvc.get().uri("/api/v1/teams").cookie(rep.accessToken())).hasStatusOk();
	}

	@Test
	void aRepCannotLeadATeam() {
		Session admin = signedIn(Role.ADMIN);
		AppUser rep = invite(uniqueEmail(), Role.REP);
		assertThat(send(this.mvc.post().uri("/api/v1/teams"), admin,
				"{\"name\":\"Led " + UUID.randomUUID() + "\",\"managerId\":\"" + rep.getId() + "\"}")).hasStatus(422);
	}

	@Test
	void anAdminCanRenameAUserAndRemoveSomeoneWhoNeverSignedIn() {
		Session admin = signedIn(Role.ADMIN);
		AppUser invited = invite(uniqueEmail(), Role.REP);

		assertThat(send(this.mvc.patch().uri("/api/v1/users/" + invited.getId()), admin, "{\"displayName\":\"Renamed Person\"}"))
			.hasStatusOk().bodyJson().extractingPath("$.displayName").isEqualTo("Renamed Person");

		assertThat(send(this.mvc.delete().uri("/api/v1/users/" + invited.getId()), admin, null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/users/" + invited.getId()), admin, null)).hasStatus(404);
	}

	@Test
	void aUserWhoHasSignedInIsDisabledNotDeleted() {
		Session admin = signedIn(Role.ADMIN);
		String email = uniqueEmail();
		AppUser user = invite(email, Role.REP);
		signIn(uniqueGoogleId(), email);

		assertThat(send(this.mvc.delete().uri("/api/v1/users/" + user.getId()), admin, null)).hasStatus(409);
		assertThat(send(this.mvc.patch().uri("/api/v1/users/" + user.getId()), admin, "{\"status\":\"DISABLED\"}")).hasStatusOk();
	}

	@Test
	void onlyAdminsDeleteUsers() {
		AppUser invited = invite(uniqueEmail(), Role.REP);
		assertThat(send(this.mvc.delete().uri("/api/v1/users/" + invited.getId()), signedIn(Role.MANAGER), null)).hasStatus(403);
	}

	@Test
	void aCurrencyCanBeAddedAndRemovedButNotTheBase() {
		Session admin = signedIn(Role.ADMIN);

		assertThat(send(this.mvc.patch().uri("/api/v1/currencies/CHF"), admin, "{\"enabled\":true}")).hasStatusOk()
			.bodyJson().extractingPath("$.enabled").isEqualTo(true);
		assertThat(send(this.mvc.patch().uri("/api/v1/currencies/CHF"), admin, "{\"enabled\":false}")).hasStatusOk();
		assertThat(send(this.mvc.delete().uri("/api/v1/currencies/CHF"), admin, null)).hasStatus(204);

		assertThat(send(this.mvc.delete().uri("/api/v1/currencies/INR"), admin, null)).hasStatus(409);
		assertThat(send(this.mvc.patch().uri("/api/v1/currencies/INR"), admin, "{\"enabled\":false}")).hasStatus(409);
		assertThat(send(this.mvc.patch().uri("/api/v1/currencies/ZZZ"), admin, "{\"enabled\":true}")).hasStatus(422);
	}

	@Test
	void aRateSetByHandCanBeTakenBack() {
		Session admin = signedIn(Role.ADMIN);
		String day = LocalDate.of(2033, 6, 1).toString();

		assertThat(send(this.mvc.put().uri("/api/v1/exchange-rates/USD/" + day), admin, "{\"rateToBase\":\"84.5\"}")).hasStatusOk();
		assertThat(send(this.mvc.delete().uri("/api/v1/exchange-rates/USD/" + day), admin, null)).hasStatus(204);
		assertThat(send(this.mvc.delete().uri("/api/v1/exchange-rates/USD/" + day), admin, null)).hasStatus(404);
	}

	@Test
	void ratesAndCurrenciesAreAdminOnlyToChange() {
		Session rep = signedIn(Role.REP);
		assertThat(send(this.mvc.put().uri("/api/v1/exchange-rates/USD/2033-06-02"), rep, "{\"rateToBase\":\"80\"}")).hasStatus(403);
		assertThat(send(this.mvc.delete().uri("/api/v1/currencies/USD"), rep, null)).hasStatus(403);
		assertThat(this.mvc.get().uri("/api/v1/currencies").cookie(rep.accessToken())).hasStatusOk();
	}

}
