package com.nexus.crm.identity.auth.dto;

/** The two tokens the browser keeps in cookies. */
public record SessionTokens(
	String accessToken,
	String refreshToken
) {
}
