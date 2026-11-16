package com.nexus.crm.support;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.nexus.crm.identity.auth.SessionService;
import com.nexus.crm.identity.auth.SessionService.ClientInfo;
import com.nexus.crm.identity.auth.SessionService.SessionTokens;
import com.nexus.crm.identity.auth.SignInService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;

/**
 * Base class for tests that run the whole app against a real PostgreSQL
 * (started with Testcontainers, so Docker must be running).
 *
 * <p>
 * All tests share one app and one database, so each test creates its own users
 * with unique emails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.Containers.class)
public abstract class IntegrationTest {

	protected static final String FRONTEND = "http://localhost:3000";

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer("postgres:17-alpine");
		}

	}

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected UserService users;

	@Autowired
	private SignInService signInService;

	@Autowired
	private SessionService sessionService;

	/** The cookies a browser holds after signing in. */
	public record Session(Cookie accessToken, Cookie refreshToken) {
	}

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@acme.example";
	}

	protected static String uniqueGoogleId() {
		return "google-" + UUID.randomUUID();
	}

	protected AppUser invite(String email, Role role) {
		return this.users.invite(email, "Test User", role, null);
	}

	/**
	 * Signs a person in, using the same services the app uses after Google
	 * confirms who they are. Tests cannot go through Google's own pages.
	 */
	protected Session signIn(String googleId, String email) {
		AppUser user = this.signInService.signIn(googleId, email, true, null);
		SessionTokens tokens = this.sessionService.start(user, new ClientInfo("test", null));
		return new Session(new Cookie("nx_at", tokens.accessToken()), new Cookie("nx_rt", tokens.refreshToken()));
	}

	/** Invites a new user with the given role and signs them in. */
	protected Session signedIn(Role role) {
		String email = uniqueEmail();
		invite(email, role);
		return signIn(uniqueGoogleId(), email);
	}

	/** Adds a matching CSRF cookie and header, as the frontend does on every request that changes data. */
	protected static MockMvcRequestBuilder withCsrf(MockMvcRequestBuilder request) {
		return request.cookie(new Cookie("XSRF-TOKEN", "csrf-test-token")).header("X-XSRF-TOKEN", "csrf-test-token");
	}

}
