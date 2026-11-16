package com.nexus.crm.identity.auth;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.nexus.crm.identity.auth.SessionService.SessionTokens;

/** Writes and clears the sign-in cookies. */
@Component
@RequiredArgsConstructor
class AuthCookies {

	/** The cookie that holds the access token. */
	static final String ACCESS_TOKEN = "nx_at";

	/** The cookie that holds the refresh token. The browser sends it to the refresh endpoint only. */
	static final String REFRESH_TOKEN = "nx_rt";

	static final String REFRESH_PATH = "/api/v1/auth/refresh";

	private final AuthProperties properties;

	void write(HttpServletResponse response, SessionTokens tokens) {
		// Both cookies last as long as the refresh token.
		Duration lifetime = this.properties.refreshToken().ttl();
		add(response, ACCESS_TOKEN, tokens.accessToken(), "/", "Lax", lifetime);
		add(response, REFRESH_TOKEN, tokens.refreshToken(), REFRESH_PATH, "Strict", lifetime);
	}

	void clear(HttpServletResponse response) {
		add(response, ACCESS_TOKEN, "", "/", "Lax", Duration.ZERO);
		add(response, REFRESH_TOKEN, "", REFRESH_PATH, "Strict", Duration.ZERO);
	}

	/** Adds a cookie to the response. A zero {@code maxAge} deletes it from the browser. */
	void add(HttpServletResponse response, String name, String value, String path, String sameSite, Duration maxAge) {
		ResponseCookie cookie = ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(this.properties.cookies().secure())
			.sameSite(sameSite)
			.path(path)
			.maxAge(maxAge)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	/** Returns the value of a cookie, or null if it is missing or empty. */
	static String read(HttpServletRequest request, String name) {
		Cookie cookie = WebUtils.getCookie(request, name);
		return (cookie != null && !cookie.getValue().isEmpty()) ? cookie.getValue() : null;
	}

}
