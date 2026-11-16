package com.nexus.crm.identity.auth;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The sign-in settings, read from {@code nexus.auth} in application.yml. */
@Validated
@ConfigurationProperties("nexus.auth")
public record AuthProperties(@NotBlank String frontendUrl, String bootstrapAdminEmail, @Valid @NotNull Jwt jwt,
		@Valid @NotNull RefreshToken refreshToken, @Valid @NotNull Cookies cookies) {

	public AuthProperties {
		// Drop any "/" at the end, so the address can be joined with a path safely.
		frontendUrl = (frontendUrl != null) ? frontendUrl.replaceAll("/+$", "") : null;
	}

	/**
	 * @param issuer the name written into each access token as its issuer
	 * @param privateKey the key used to sign access tokens (PEM text)
	 * @param publicKey the matching key used to check them (PEM text)
	 * @param allowEphemeralKey if no key is set, make a temporary one at startup; for local development and tests only
	 * @param accessTokenTtl how long an access token is valid
	 */
	public record Jwt(@NotBlank String issuer, String privateKey, String publicKey, boolean allowEphemeralKey,
			@NotNull Duration accessTokenTtl) {
	}

	/**
	 * @param ttl how long a refresh token is valid, so how long someone stays signed in without using the app
	 * @param reuseGrace how soon after use the same refresh token may be used again without being treated as
	 * stolen; this happens when two browser tabs refresh at the same time
	 */
	public record RefreshToken(@NotNull Duration ttl, @NotNull Duration reuseGrace) {
	}

	/**
	 * @param secure send cookies over HTTPS only; keep this on everywhere except local development
	 */
	public record Cookies(boolean secure) {
	}

}
