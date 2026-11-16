package com.nexus.crm.identity.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.nexus.crm.identity.auth.SignInService.SignInRefusedException;
import com.nexus.crm.identity.auth.dto.ClientInfo;
import com.nexus.crm.identity.user.AppUser;

/** Runs when Google sends the user back to us. */
@Slf4j
@Component
@RequiredArgsConstructor
class OidcSignInHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	/** Used for any unexpected problem. The login page shows a general message for it. */
	private static final String SIGN_IN_FAILED = "sign_in_failed";

	private final SignInService signIn;

	private final SessionService sessions;

	private final AuthCookies cookies;

	private final AuthProperties properties;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OidcUser google = (OidcUser) authentication.getPrincipal();
		try {
			AppUser user = this.signIn.signIn(google.getSubject(), google.getEmail(),
					Boolean.TRUE.equals(google.getEmailVerified()), google.getPicture());
			this.cookies.write(response, this.sessions.start(user, ClientInfo.from(request)));
			log.info("Sign-in succeeded: provider=google userId={}", user.getId());
			response.sendRedirect(this.properties.frontendUrl() + "/");
		}
		catch (SignInRefusedException ex) {
			log.warn("Sign-in refused: provider=google reason={} email={} detail={}", ex.getCode(),
					maskEmail(google.getEmail()), ex.getMessage());
			redirectToSignIn(response, ex.getCode());
		}
		catch (RuntimeException ex) {
			log.error("Sign-in failed unexpectedly: provider=google", ex);
			redirectToSignIn(response, SIGN_IN_FAILED);
		}
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		String providerError = (exception instanceof OAuth2AuthenticationException oauth)
				? oauth.getError().getErrorCode() : "unknown";
		// access_denied means the user cancelled at Google. That is not an error.
		if (OAuth2ErrorCodes.ACCESS_DENIED.equals(providerError)) {
			log.info("Sign-in cancelled: provider=google");
			redirectToSignIn(response, OAuth2ErrorCodes.ACCESS_DENIED);
			return;
		}
		log.warn("Sign-in failed: provider=google error={}", providerError);
		redirectToSignIn(response, SIGN_IN_FAILED);
	}

	private void redirectToSignIn(HttpServletResponse response, String errorCode) throws IOException {
		response.sendRedirect(this.properties.frontendUrl() + "/login?error=" + errorCode);
	}

	/** Hides most of an email for the logs: jane.doe@acme.example becomes j***@acme.example. */
	private static String maskEmail(String email) {
		if (email == null) {
			return "none";
		}
		int at = email.indexOf('@');
		return (at < 1) ? "***" : email.charAt(0) + "***" + email.substring(at);
	}

}
