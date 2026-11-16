package com.nexus.crm.identity.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

/**
 * Remembers a sign-in that is in progress, from the moment we send the user to
 * Google until Google sends them back. Spring normally keeps this in a server
 * session; we keep it in a 5-minute cookie instead, so the server stores nothing.
 *
 * <p>
 * The cookie is encrypted. If someone changes it, it no longer decrypts and
 * we ignore it.
 */
@Component
@RequiredArgsConstructor
class CookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	static final String COOKIE = "nx_oauth";

	private static final String PATH = "/api/v1/auth";

	/** How long the user has to finish signing in at Google. */
	private static final Duration LIFETIME = Duration.ofMinutes(5);

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	/** Extra safety: only these kinds of objects may be read back from the cookie. */
	private static final ObjectInputFilter ALLOWED_CLASSES = ObjectInputFilter.Config.createFilter(
			"java.lang.*;java.util.*;org.springframework.security.oauth2.core.*;"
					+ "org.springframework.security.oauth2.core.endpoint.*;!*");

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKey key;

	private final AuthCookies cookies;

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		String value = AuthCookies.read(request, COOKIE);
		String state = request.getParameter(OAuth2ParameterNames.STATE);
		if (value == null || state == null) {
			return null;
		}
		OAuth2AuthorizationRequest stored = decrypt(value);
		// Only accept the reply from Google if this same browser started the sign-in.
		return (stored != null && state.equals(stored.getState())) ? stored : null;
	}

	@Override
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
			HttpServletResponse response) {
		if (authorizationRequest == null) {
			clear(response);
			return;
		}
		// SameSite must be Lax here, or the browser would not send the cookie when coming back from Google.
		this.cookies.add(response, COOKIE, encrypt(authorizationRequest), PATH, "Lax", LIFETIME);
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
			HttpServletResponse response) {
		OAuth2AuthorizationRequest stored = loadAuthorizationRequest(request);
		if (AuthCookies.read(request, COOKIE) != null) {
			clear(response);
		}
		return stored;
	}

	private void clear(HttpServletResponse response) {
		this.cookies.add(response, COOKIE, "", PATH, "Lax", Duration.ZERO);
	}

	private String encrypt(OAuth2AuthorizationRequest authorizationRequest) {
		try {
			ByteArrayOutputStream serialized = new ByteArrayOutputStream();
			try (ObjectOutputStream out = new ObjectOutputStream(serialized)) {
				out.writeObject(authorizationRequest);
			}
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(serialized.toByteArray());
			byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot store the authorization request", ex);
		}
	}

	private OAuth2AuthorizationRequest decrypt(String value) {
		try {
			byte[] payload = Base64.getUrlDecoder().decode(value);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
			byte[] serialized = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
			try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
				in.setObjectInputFilter(ALLOWED_CLASSES);
				return (OAuth2AuthorizationRequest) in.readObject();
			}
		}
		catch (Exception ex) {
			// The cookie was changed or is unreadable. Treat it as if there were no cookie.
			return null;
		}
	}

}
