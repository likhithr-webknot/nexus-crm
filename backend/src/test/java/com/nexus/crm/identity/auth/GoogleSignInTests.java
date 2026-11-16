package com.nexus.crm.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;
import com.nexus.crm.identity.auth.CookieOAuth2AuthorizationRequestRepository;
import com.nexus.crm.identity.auth.OidcSignInHandlers;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.AppUserRepository;

/** Google sign-in: what we send to Google, and what we do when Google sends the user back. These tests never contact Google. */
class GoogleSignInTests extends IntegrationTest {

	private static final String START = "/api/v1/auth/oauth2/authorization/google";

	private static final String CALLBACK = "/api/v1/auth/login/oauth2/code/google";

	@Autowired
	private OidcSignInHandlers handlers;

	@Autowired
	private CookieOAuth2AuthorizationRequestRepository pendingSignIns;

	@Autowired
	private AppUserRepository userRepository;

	@Test
	void startSendsTheUserToGoogleWithStateNonceAndPkce() {
		MvcTestResult start = this.mvc.get().uri(START).exchange();

		assertThat(start).hasStatus(302);
		assertThat(start.getResponse().getRedirectedUrl()).startsWith("https://accounts.google.com/");
		MultiValueMap<String, String> sent = queryOf(start);
		assertThat(sent.getFirst("client_id")).isEqualTo("test-client");
		assertThat(decode(sent.getFirst("redirect_uri"))).isEqualTo("http://localhost" + CALLBACK);
		assertThat(sent.getFirst("state")).isNotBlank();
		assertThat(sent.getFirst("nonce")).isNotBlank();
		assertThat(sent.getFirst("code_challenge")).isNotBlank();
		assertThat(sent.getFirst("code_challenge_method")).isEqualTo("S256");
		assertThat(sent.getFirst("prompt")).isEqualTo("select_account");

		Cookie pending = start.getResponse().getCookie("nx_oauth");
		assertThat(pending.isHttpOnly()).isTrue();
		assertThat(pending.getSecure()).isTrue();
		assertThat(pending.getMaxAge()).isEqualTo(300);
		// The sign-in in progress is kept in that cookie, not in a server session.
		assertThat(start.getRequest().getSession(false)).isNull();
	}

	@Test
	void theSignInCookieIsOnlyAcceptedUnchangedAndWithItsOwnState() {
		MvcTestResult start = this.mvc.get().uri(START).exchange();
		Cookie pending = start.getResponse().getCookie("nx_oauth");
		String state = decode(queryOf(start).getFirst("state"));

		assertThat(this.pendingSignIns.loadAuthorizationRequest(callbackRequest(pending, state)).getState())
			.isEqualTo(state);
		assertThat(this.pendingSignIns.loadAuthorizationRequest(callbackRequest(pending, "another-state"))).isNull();
		Cookie changed = new Cookie("nx_oauth", pending.getValue().substring(0, pending.getValue().length() - 4) + "AAAA");
		assertThat(this.pendingSignIns.loadAuthorizationRequest(callbackRequest(changed, state))).isNull();
	}

	@Test
	void callbackWithoutASignInStartedInThisBrowserIsRejected() {
		MvcTestResult callback = this.mvc.get()
			.uri(CALLBACK)
			.param("code", "stolen-code")
			.param("state", "attacker-state")
			.cookie(new Cookie("nx_oauth", "bm90LWVuY3J5cHRlZC1ieS11cw"))
			.exchange();

		assertRefused(callback.getResponse(), "sign_in_failed");
	}

	@Test
	void cancellingAtGoogleReturnsToTheLoginPage() {
		MvcTestResult start = this.mvc.get().uri(START).exchange();

		MvcTestResult callback = this.mvc.get()
			.uri(CALLBACK)
			.param("error", "access_denied")
			.param("state", decode(queryOf(start).getFirst("state")))
			.cookie(start.getResponse().getCookie("nx_oauth"))
			.exchange();

		assertRefused(callback.getResponse(), "access_denied");
	}

	@Test
	void invitedUserIsSignedInAndReceivesTheSessionCookies() throws Exception {
		String email = uniqueEmail();
		AppUser invited = invite(email, Role.REP);

		MockHttpServletResponse response = googleSendsBack(uniqueGoogleId(), email, true);

		assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/");
		Cookie access = response.getCookie("nx_at");
		assertThat(access.isHttpOnly()).isTrue();
		assertThat(access.getSecure()).isTrue();
		assertThat(access.getPath()).isEqualTo("/");
		assertThat(access.getAttribute("SameSite")).isEqualTo("Lax");
		// Signed with ES256 and carrying a key ID, so the signing key can be replaced later.
		JWSHeader header = SignedJWT.parse(access.getValue()).getHeader();
		assertThat(header.getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
		assertThat(header.getKeyID()).isNotBlank();
		Cookie refresh = response.getCookie("nx_rt");
		assertThat(refresh.isHttpOnly()).isTrue();
		assertThat(refresh.getSecure()).isTrue();
		assertThat(refresh.getPath()).isEqualTo("/api/v1/auth/refresh");
		assertThat(refresh.getAttribute("SameSite")).isEqualTo("Strict");

		AppUser user = this.userRepository.findById(invited.getId()).orElseThrow();
		assertThat(user.getStatus()).isEqualTo(AppUser.Status.ACTIVE);
		assertThat(user.getLastLoginAt()).isNotNull();
		assertThat(user.getAvatarUrl()).startsWith("https://avatars.example/");

		assertThat(this.mvc.get().uri("/api/v1/me").cookie(access)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo(email);
	}

	@Test
	void invitationEmailIsMatchedIgnoringCase() {
		String email = uniqueEmail();
		invite(email, Role.REP);

		assertThat(googleSendsBack(uniqueGoogleId(), email.toUpperCase(), true).getRedirectedUrl())
			.isEqualTo(FRONTEND + "/");
	}

	@Test
	void someoneWhoWasNotInvitedIsRefusedAndNoAccountIsCreated() {
		String email = uniqueEmail();

		assertRefused(googleSendsBack(uniqueGoogleId(), email, true), "not_invited");
		assertThat(this.userRepository.existsByEmail(email)).isFalse();
	}

	@Test
	void unverifiedEmailCannotClaimAnInvitation() {
		String email = uniqueEmail();
		invite(email, Role.REP);

		assertRefused(googleSendsBack(uniqueGoogleId(), email, false), "email_unverified");
	}

	@Test
	void disabledUserIsRefusedBeforeAndAfterTheirFirstSignIn() {
		String neverSignedIn = uniqueEmail();
		AppUser first = invite(neverSignedIn, Role.REP);
		this.users.update(first.getId(), null, AppUser.Status.DISABLED, UUID.randomUUID());
		assertRefused(googleSendsBack(uniqueGoogleId(), neverSignedIn, true), "account_disabled");

		String returning = uniqueEmail();
		String googleId = uniqueGoogleId();
		AppUser second = invite(returning, Role.REP);
		assertThat(googleSendsBack(googleId, returning, true).getRedirectedUrl()).isEqualTo(FRONTEND + "/");
		this.users.update(second.getId(), null, AppUser.Status.DISABLED, UUID.randomUUID());
		assertRefused(googleSendsBack(googleId, returning, true), "account_disabled");
	}

	@Test
	void returningUserIsRecognisedByGoogleIdEvenAfterTheirEmailChanges() {
		String email = uniqueEmail();
		String googleId = uniqueGoogleId();
		invite(email, Role.REP);
		googleSendsBack(googleId, email, true);

		assertThat(googleSendsBack(googleId, uniqueEmail(), true).getRedirectedUrl()).isEqualTo(FRONTEND + "/");
	}

	@Test
	void aDifferentGoogleAccountWithALinkedUsersEmailIsRefused() {
		String email = uniqueEmail();
		invite(email, Role.ADMIN);
		googleSendsBack(uniqueGoogleId(), email, true);

		// The address now belongs to a different person at Google.
		assertRefused(googleSendsBack(uniqueGoogleId(), email, true), "not_invited");
	}

	/** Runs what the app does once Google has confirmed who the person is. */
	private MockHttpServletResponse googleSendsBack(String googleId, String email, boolean emailVerified) {
		Instant now = Instant.now();
		OidcUser google = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")),
				new OidcIdToken("id-token", now, now.plusSeconds(300), Map.of("sub", googleId, "email", email,
						"email_verified", emailVerified, "picture", "https://avatars.example/" + googleId + ".png")));
		MockHttpServletResponse response = new MockHttpServletResponse();
		try {
			this.handlers.onAuthenticationSuccess(new MockHttpServletRequest(), response,
					new TestingAuthenticationToken(google, null));
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
		return response;
	}

	private static void assertRefused(MockHttpServletResponse response, String errorCode) {
		assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=" + errorCode);
		assertThat(response.getCookie("nx_at")).isNull();
		assertThat(response.getCookie("nx_rt")).isNull();
	}

	private static MockHttpServletRequest callbackRequest(Cookie pending, String state) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", CALLBACK);
		request.setCookies(pending);
		request.setParameter("state", state);
		return request;
	}

	private static MultiValueMap<String, String> queryOf(MvcTestResult redirect) {
		return UriComponentsBuilder.fromUriString(redirect.getResponse().getRedirectedUrl()).build().getQueryParams();
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

}
