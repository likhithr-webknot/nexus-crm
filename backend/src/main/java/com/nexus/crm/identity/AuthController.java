package com.nexus.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.identity.SessionService.ClientInfo;
import com.nexus.crm.identity.SessionService.SessionTokens;
import com.nexus.crm.shared.ApiException;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.ErrorCode;

/**
 * Refresh and sign-out. Signing in has no method here: Spring Security handles
 * the Google sign-in itself (see SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

	private final SessionService sessions;

	private final AuthCookies cookies;

	/**
	 * Gives the browser new tokens. The frontend calls this when a request fails with token_expired, then tries
	 * that request again.
	 */
	@PostMapping("/refresh")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void refresh(HttpServletRequest request, HttpServletResponse response) {
		String refreshToken = AuthCookies.read(request, AuthCookies.REFRESH_TOKEN);
		SessionTokens tokens = (refreshToken != null)
				? this.sessions.refresh(refreshToken, ClientInfo.from(request)).orElse(null) : null;
		if (tokens == null) {
			// The cookies no longer work, so remove them.
			this.cookies.clear(response);
			throw new ApiException(ErrorCode.NOT_AUTHENTICATED, "The session has ended. Sign in again.");
		}
		this.cookies.write(response, tokens);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@AuthenticationPrincipal CurrentUser user, HttpServletResponse response) {
		this.sessions.end(user.sessionId());
		this.cookies.clear(response);
		log.info("Sign-out: userId={}", user.id());
	}

	/** Signs the user out everywhere, not only in this browser. */
	@PostMapping("/logout-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logoutAll(@AuthenticationPrincipal CurrentUser user, HttpServletResponse response) {
		this.sessions.endAll(user.id());
		this.cookies.clear(response);
		log.info("Sign-out of all devices: userId={}", user.id());
	}

}
