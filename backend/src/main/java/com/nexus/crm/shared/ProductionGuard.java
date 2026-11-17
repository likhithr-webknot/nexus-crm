package com.nexus.crm.shared;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with unsafe settings when {@code NEXUS_ENVIRONMENT=production}. The defaults suit a laptop (no
 * database password, a throwaway signing key), and a forgotten variable should stop the deploy, not run exposed.
 */
@Slf4j
@Component
class ProductionGuard {

	ProductionGuard(Environment environment) {
		List<String> problems = problems(environment);
		if ("production".equalsIgnoreCase(environment.getProperty("nexus.environment", "local"))) {
			if (!problems.isEmpty()) {
				throw new IllegalStateException("Unsafe settings for production:\n - " + String.join("\n - ", problems));
			}
		}
		else if (!problems.isEmpty()) {
			log.warn("These settings are fine on a laptop but not in production (set NEXUS_ENVIRONMENT=production to enforce them): {}", problems);
		}
	}

	static List<String> problems(Environment environment) {
		List<String> problems = new ArrayList<>();
		if (blank(environment.getProperty("spring.datasource.password"))) {
			problems.add("NEXUS_DB_PASSWORD is empty");
		}
		if (environment.getProperty("nexus.auth.jwt.allow-ephemeral-key", Boolean.class, false)) {
			problems.add("NEXUS_JWT_ALLOW_EPHEMERAL_KEY is on: the signing key would be lost at every restart");
		}
		if (!environment.getProperty("nexus.auth.cookies.secure", Boolean.class, true)) {
			problems.add("NEXUS_COOKIES_SECURE is off: sign-in cookies would travel over plain HTTP");
		}
		if (!environment.getProperty("nexus.auth.frontend-url", "").startsWith("https://")) {
			problems.add("NEXUS_FRONTEND_URL is not an https:// address");
		}
		return problems;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
