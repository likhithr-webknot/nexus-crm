package com.nexus.crm.shared.web;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a short ID. The ID is sent back in the X-Request-Id
 * header, shown in error responses and added to every log line, so one
 * request can be found in the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

	private static final String MDC_KEY = "requestId";

	private static final SecureRandom RANDOM = new SecureRandom();

	/** Returns the ID of the given request. */
	public static String current(HttpServletRequest request) {
		return (String) request.getAttribute(ATTRIBUTE);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// We always make our own ID and never accept one from the caller.
		byte[] bytes = new byte[6];
		RANDOM.nextBytes(bytes);
		String requestId = HexFormat.of().formatHex(bytes);
		request.setAttribute(ATTRIBUTE, requestId);
		response.setHeader(HEADER, requestId);
		MDC.put(MDC_KEY, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

}
