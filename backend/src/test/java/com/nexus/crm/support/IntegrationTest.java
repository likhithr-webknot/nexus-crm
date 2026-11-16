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
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.nexus.crm.identity.auth.SessionService;
import com.nexus.crm.identity.auth.SignInService;
import com.nexus.crm.identity.auth.dto.ClientInfo;
import com.nexus.crm.identity.auth.dto.SessionTokens;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.Role;

/**
 * Base class for tests that run the whole app against a real PostgreSQL (started with Testcontainers, so Docker
 * must be running).
 *
 * <p>
 * Problem we had in CI (2026-11-16): two runs failed with "ApplicationContext failure threshold (1) exceeded".
 * That message only means "the first attempt to start the app already failed, so I will not try again for every
 * other test". The real cause is the first error in the log, and there were two:
 * <ol>
 * <li>{@code spring.datasource.hikari.connection-timeout} was written as {@code 10s}. Hikari needs milliseconds.
 * <li>This class was edited temporarily to point at a local database so tests could run without Docker, and a
 * commit happened while it was edited. CI has no PostgreSQL on localhost, so every test failed with
 * "Connection to localhost:5432 refused".
 * </ol>
 * How we fixed it, in order: (1) read the first {@code Caused by} in the CI log of each failed run;
 * (2) changed the timeout to {@code 10000}; (3) restored this class and stopped editing it: local runs without
 * Docker now happen in a throwaway copy of the project; (4) added {@code TestSetupGuardTests}, which fails with a
 * clear message if this class stops using Testcontainers.
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

	/** Signs a person in, using the same services the app uses after Google confirms who they are. */
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
