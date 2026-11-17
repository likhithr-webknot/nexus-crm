package com.nexus.crm.shared.limits;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Stops one address from flooding the sign-in and token refresh endpoints. The limits are loose on purpose: an office
 * shares one address. Behind a reverse proxy this only works with NEXUS_FORWARD_HEADERS=framework, otherwise every
 * visitor looks like the proxy.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final String SIGN_IN = "/api/v1/auth/oauth2/";

	private static final String GOOGLE_REPLY = "/api/v1/auth/login/";

	private static final String REFRESH = "/api/v1/auth/refresh";

	private final RateLimit signIn = new RateLimit(60, Duration.ofMinutes(1));

	private final RateLimit refresh = new RateLimit(120, Duration.ofMinutes(1));

	private final HandlerExceptionResolver errors;

	private final String frontendUrl;

	public AuthRateLimitFilter(HandlerExceptionResolver errors, String frontendUrl) {
		this.errors = errors;
		this.frontendUrl = frontendUrl;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = request.getRequestURI();
		String address = request.getRemoteAddr();
		if ((path.startsWith(SIGN_IN) || path.startsWith(GOOGLE_REPLY)) && !this.signIn.tryAcquire(address)) {
			// A browser is navigating here, so send it to the login page, which shows a message.
			response.sendRedirect(this.frontendUrl + "/login?error=rate_limited");
			return;
		}
		if (path.equals(REFRESH) && !this.refresh.tryAcquire(address)) {
			this.errors.resolveException(request, response, null,
					new ApiException(ErrorCode.RATE_LIMITED, "Too many requests. Wait a while and try again."));
			return;
		}
		chain.doFilter(request, response);
	}

}
