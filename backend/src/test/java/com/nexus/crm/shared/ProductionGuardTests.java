package com.nexus.crm.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionGuardTests {

	private static MockEnvironment safe() {
		return new MockEnvironment()
			.withProperty("nexus.environment", "production")
			.withProperty("spring.datasource.password", "a-real-password")
			.withProperty("nexus.auth.cookies.secure", "true")
			.withProperty("nexus.auth.jwt.allow-ephemeral-key", "false")
			.withProperty("nexus.auth.frontend-url", "https://crm.example.com");
	}

	@Test
	void safeSettingsStart() {
		new ProductionGuard(safe());
	}

	@Test
	void everyUnsafeSettingIsReportedTogether() {
		MockEnvironment unsafe = safe()
			.withProperty("spring.datasource.password", "")
			.withProperty("nexus.auth.cookies.secure", "false")
			.withProperty("nexus.auth.jwt.allow-ephemeral-key", "true")
			.withProperty("nexus.auth.frontend-url", "http://crm.example.com");

		assertThatThrownBy(() -> new ProductionGuard(unsafe)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("NEXUS_DB_PASSWORD")
			.hasMessageContaining("NEXUS_COOKIES_SECURE")
			.hasMessageContaining("NEXUS_JWT_ALLOW_EPHEMERAL_KEY")
			.hasMessageContaining("NEXUS_FRONTEND_URL");
	}

	@Test
	void onALaptopTheSameSettingsOnlyWarn() {
		MockEnvironment local = new MockEnvironment().withProperty("spring.datasource.password", "");

		assertThat(ProductionGuard.problems(local)).isNotEmpty();
		new ProductionGuard(local);
	}

}
