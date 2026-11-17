package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.nexus.crm.support.IntegrationTest;

/** Every API response carries the headers that stop it being framed, sniffed, cached or leaked through the referrer. */
class SecurityHeadersTests extends IntegrationTest {

	@Test
	void anApiResponseCarriesTheSecurityHeaders() {
		var response = this.mvc.get().uri("/api/v1/me").exchange().getResponse();

		assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'").contains("frame-ancestors 'none'");
		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		assertThat(response.getHeader("Permissions-Policy")).contains("camera=()").contains("microphone=()");
		assertThat(response.getHeader("Cache-Control")).contains("no-store");
	}

	@Test
	void httpsResponsesAskBrowsersToStayOnHttps() {
		var response = this.mvc.get().uri("/api/v1/me").secure(true).exchange().getResponse();

		assertThat(response.getHeader("Strict-Transport-Security")).contains("max-age=63072000").contains("includeSubDomains");
	}

	@Test
	void theSignInRedirectsAreCoveredToo() {
		var response = this.mvc.get().uri("/api/v1/auth/oauth2/authorization/google").exchange().getResponse();

		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
	}

}
