package com.nexus.crm.identity.auth.dto;

import java.net.InetAddress;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** The browser and IP address a request came from. */
public record ClientInfo(
	String userAgent,
	InetAddress ip
) {

	public static ClientInfo from(HttpServletRequest request) {
		String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
		if (userAgent != null && userAgent.length() > 512) {
			userAgent = userAgent.substring(0, 512);
		}
		InetAddress ip = null;
		try {
			// ofLiteral only parses the text; it never does a network lookup.
			ip = InetAddress.ofLiteral(request.getRemoteAddr());
		}
		catch (IllegalArgumentException ex) {
			// Not a valid IP address. It is only for information, so continue without it.
		}
		return new ClientInfo(userAgent, ip);
	}

}
