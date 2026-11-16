package com.nexus.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Access-token validation, refresh-token rotation and reuse detection, sign-out, CSRF. */
class SessionTests extends IntegrationTest {

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void requestWithoutATokenIsNotAuthenticated() {
		MvcTestResult result = this.mvc.get().uri("/api/v1/me").exchange();

		assertThat(result).hasStatus(401).hasContentType("application/problem+json");
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("not_authenticated");
		assertThat(result).bodyJson()
			.extractingPath("$.requestId")
			.isEqualTo(result.getResponse().getHeader("X-Request-Id"));
	}

	@Test
	void expiredTokenIsReportedAsExpiredSoTheFrontendKnowsToRefresh() {
		Instant past = Instant.now().minusSeconds(3600);
		String expired = this.jwtEncoder
			.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(),
					JwtClaimsSet.builder()
						.issuer("nexus-crm")
						.subject(UUID.randomUUID().toString())
						.claim("sid", UUID.randomUUID().toString())
						.issuedAt(past)
						.expiresAt(past.plusSeconds(900))
						.build()))
			.getTokenValue();

		assertThat(this.mvc.get().uri("/api/v1/me").cookie(new Cookie("nx_at", expired))).hasStatus(401)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("token_expired");
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		ECKey attackerKey = new ECKeyGenerator(Curve.P_256).generate();
		Instant now = Instant.now();
		SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).build(),
				new JWTClaimsSet.Builder().issuer("nexus-crm")
					.subject(UUID.randomUUID().toString())
					.claim("sid", UUID.randomUUID().toString())
					.claim("role", "ADMIN")
					.issueTime(java.util.Date.from(now))
					.expirationTime(java.util.Date.from(now.plusSeconds(900)))
					.build());
		forged.sign(new ECDSASigner(attackerKey));

		assertThat(this.mvc.get().uri("/api/v1/me").cookie(new Cookie("nx_at", forged.serialize()))).hasStatus(401)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("not_authenticated");
	}

	@Test
	void refreshRotatesTheTokenAndWorksWithAnExpiredAccessCookie() {
		Session session = signedIn(Role.REP);

		MvcTestResult refreshed = this.mvc.post()
			.uri("/api/v1/auth/refresh")
			.cookie(new Cookie("nx_at", "expired-or-garbage"), session.refreshToken())
			.exchange();

		assertThat(refreshed).hasStatus(204);
		Cookie newRefresh = refreshed.getResponse().getCookie("nx_rt");
		assertThat(newRefresh.getValue()).isNotEqualTo(session.refreshToken().getValue());
		assertThat(this.mvc.get().uri("/api/v1/me").cookie(refreshed.getResponse().getCookie("nx_at"))).hasStatusOk();
	}

	@Test
	void reusingAnExchangedRefreshTokenEndsTheWholeSession() {
		Session session = signedIn(Role.REP);
		Cookie rotated = refresh(session.refreshToken()).getResponse().getCookie("nx_rt");
		// Move the exchange outside the grace period that tolerates two tabs refreshing at once.
		this.jdbc.sql("UPDATE refresh_token SET used_at = used_at - interval '1 minute' WHERE used_at IS NOT NULL")
			.update();

		MvcTestResult reuse = refresh(session.refreshToken());

		assertThat(reuse).hasStatus(401).bodyJson().extractingPath("$.code").isEqualTo("not_authenticated");
		assertThat(reuse.getResponse().getCookie("nx_at").getMaxAge()).isZero();
		// The thief and the owner are indistinguishable, so the newest token dies too.
		assertThat(refresh(rotated)).hasStatus(401);
	}

	@Test
	void twoTabsRefreshingAtTheSameMomentBothSucceed() {
		Session session = signedIn(Role.REP);

		assertThat(refresh(session.refreshToken())).hasStatus(204);
		MvcTestResult secondTab = refresh(session.refreshToken());

		assertThat(secondTab).hasStatus(204);
		assertThat(refresh(secondTab.getResponse().getCookie("nx_rt"))).hasStatus(204);
	}

	@Test
	void refreshWithoutOrWithAnUnknownTokenIsRefused() {
		assertThat(this.mvc.post().uri("/api/v1/auth/refresh")).hasStatus(401);
		assertThat(refresh(new Cookie("nx_rt", "never-issued"))).hasStatus(401);
	}

	@Test
	void stateChangingRequestNeedsTheCsrfToken() {
		Session session = signedIn(Role.REP);

		MvcTestResult withoutToken = this.mvc.post()
			.uri("/api/v1/auth/logout")
			.cookie(session.accessToken())
			.exchange();
		assertThat(withoutToken).hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("csrf_invalid");

		MvcTestResult mismatched = this.mvc.post()
			.uri("/api/v1/auth/logout")
			.cookie(session.accessToken(), new Cookie("XSRF-TOKEN", "one"))
			.header("X-XSRF-TOKEN", "another")
			.exchange();
		assertThat(mismatched).hasStatus(403);
		// Neither attempt signed the user out.
		assertThat(refresh(session.refreshToken())).hasStatus(204);
	}

	@Test
	void readRequestsHandTheFrontendACsrfCookieItCanRead() {
		Session session = signedIn(Role.REP);

		Cookie csrf = this.mvc.get()
			.uri("/api/v1/me")
			.cookie(session.accessToken())
			.exchange()
			.getResponse()
			.getCookie("XSRF-TOKEN");

		assertThat(csrf.getValue()).isNotBlank();
		assertThat(csrf.isHttpOnly()).isFalse();
	}

	@Test
	void signOutEndsThisSessionOnly() {
		String email = uniqueEmail();
		invite(email, Role.REP);
		String subject = uniqueGoogleId();
		Session laptop = signIn(subject, email);
		Session phone = signIn(subject, email);

		MvcTestResult logout = withCsrf(this.mvc.post().uri("/api/v1/auth/logout")).cookie(laptop.accessToken())
			.exchange();

		assertThat(logout).hasStatus(204);
		assertThat(logout.getResponse().getCookie("nx_at").getMaxAge()).isZero();
		assertThat(logout.getResponse().getCookie("nx_rt").getMaxAge()).isZero();
		assertThat(refresh(laptop.refreshToken())).hasStatus(401);
		assertThat(refresh(phone.refreshToken())).hasStatus(204);
	}

	@Test
	void signOutOfAllDevicesEndsEverySession() {
		String email = uniqueEmail();
		invite(email, Role.REP);
		String subject = uniqueGoogleId();
		Session laptop = signIn(subject, email);
		Session phone = signIn(subject, email);

		assertThat(withCsrf(this.mvc.post().uri("/api/v1/auth/logout-all")).cookie(laptop.accessToken()))
			.hasStatus(204);

		assertThat(refresh(laptop.refreshToken())).hasStatus(401);
		assertThat(refresh(phone.refreshToken())).hasStatus(401);
	}

	private MvcTestResult refresh(Cookie refreshToken) {
		return this.mvc.post().uri("/api/v1/auth/refresh").cookie(refreshToken).exchange();
	}

}
