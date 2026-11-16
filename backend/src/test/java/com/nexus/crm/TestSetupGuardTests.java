package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the test setup against the CI failure of 2026-11-16 (see {@code IntegrationTest}): the database for
 * integration tests must come from Testcontainers, never from a fixed local address.
 */
class TestSetupGuardTests {

	private static final Path INTEGRATION_TEST = Path.of("src/test/java/com/nexus/crm/support/IntegrationTest.java");

	@Test
	void integrationTestsGetTheirDatabaseFromTestcontainers() throws IOException {
		String source = Files.readString(INTEGRATION_TEST);

		assertThat(source).as("IntegrationTest must start PostgreSQL with Testcontainers").contains("PostgreSQLContainer");
		assertThat(source).as("IntegrationTest must not point at a fixed database. CI has none, so every test would fail.")
			.doesNotContain("jdbc:postgresql://localhost")
			.doesNotContain("spring.datasource.url");
	}

}
