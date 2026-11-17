package com.nexus.crm.mail;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.shared.SecretBox;

/**
 * Talks to Google for features that run in the background (calendar sync, inbox sync). It uses the connection a user made
 * under Settings: the refresh token is decrypted here, traded for a short-lived access token, and never logged or returned.
 */
@Component
@RequiredArgsConstructor
public class GoogleApi {

	public static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events";

	public static final String GMAIL_READ_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

	public static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

	/** What came back from Google. {@code body} is the raw text, empty for 204. */
	public record Response(int status, String body) {

		public boolean ok() {
			return this.status >= 200 && this.status < 300;
		}

	}

	/** The user never connected Google, or did not allow what this feature needs. */
	public static final class NotConnected extends RuntimeException {

		public NotConnected(String message) {
			super(message);
		}

	}

	/** Google no longer accepts the stored permission (the user removed it, or it expired). The user must connect again. */
	public static final class Revoked extends RuntimeException {

		public Revoked() {
			super("Google no longer accepts the saved permission.");
		}

	}

	/** Google could not be reached, or answered with an error that is worth retrying later. */
	public static final class Unavailable extends RuntimeException {

		public Unavailable(String message) {
			super(message);
		}

	}

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	@Value("${GOOGLE_CLIENT_ID:}")
	private String clientId;

	@Value("${GOOGLE_CLIENT_SECRET:}")
	private String clientSecret;

	@Value("${NEXUS_MAIL_TOKEN_KEY:}")
	private String tokenKey;

	public boolean configured() {
		return !this.clientId.isBlank() && !this.clientSecret.isBlank() && SecretBox.of(this.tokenKey, null) != null;
	}

	/**
	 * An access token for the user, if they allowed {@code scope}. Marks the connection as needing a reconnect when
	 * Google rejects the refresh token.
	 */
	public String accessToken(UUID userId, String scope) {
		var row = this.jdbc.sql("select refresh_token_cipher, scopes, needs_reconnect from mail_connection where user_id = :user")
			.param("user", userId)
			.query((rs, i) -> new Object[] { rs.getBytes(1), rs.getString(2), rs.getBoolean(3) })
			.optional()
			.orElseThrow(() -> new NotConnected("Google is not connected."));
		if (!configured() || !GoogleScopes.has((String) row[1], scope)) {
			throw new NotConnected("This feature was not allowed on the Google connection.");
		}
		if ((Boolean) row[2]) {
			throw new Revoked();
		}
		SecretBox box = SecretBox.of(this.tokenKey, null);
		String refresh = box.decrypt((byte[]) row[0]);
		Response response = post("https://oauth2.googleapis.com/token", "application/x-www-form-urlencoded",
				"refresh_token=" + enc(refresh) + "&client_id=" + enc(this.clientId) + "&client_secret=" + enc(this.clientSecret)
						+ "&grant_type=refresh_token", null);
		if (response.status() == 400 || response.status() == 401) {
			if (response.body().contains("invalid_grant")) {
				this.jdbc.sql("update mail_connection set needs_reconnect = true where user_id = :user").param("user", userId).update();
				throw new Revoked();
			}
			throw new Unavailable("Google refused the token request (HTTP " + response.status() + ").");
		}
		if (!response.ok()) {
			throw new Unavailable("Google token request failed (HTTP " + response.status() + ").");
		}
		Object token = parse(response.body()).get("access_token");
		if (!(token instanceof String access) || access.isEmpty()) {
			throw new Unavailable("Google sent no access token.");
		}
		return access;
	}

	/**
	 * Cancels the whole grant behind a refresh token, so the app can no longer use it and the user's Google account stops
	 * listing it. Returns false when Google could not be reached or refused. A token Google no longer knows counts as done.
	 */
	public boolean revoke(String refreshToken) {
		try {
			Response response = post("https://oauth2.googleapis.com/revoke", "application/x-www-form-urlencoded",
					"token=" + enc(refreshToken), null);
			return response.ok() || (response.status() == 400 && response.body().contains("invalid_token"));
		}
		catch (Unavailable ex) {
			return false;
		}
	}

	/** Posts a form (used to trade a code or refresh token for an access token). */
	public Response postForm(String url, String form) {
		return post(url, "application/x-www-form-urlencoded", form, null);
	}

	public Response call(String method, String url, String bearer, String jsonBody) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + bearer);
		if (jsonBody == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		else {
			request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
		}
		return send(request.build());
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> parse(String body) {
		try {
			return this.json.readValue(body, Map.class);
		}
		catch (RuntimeException ex) {
			return Map.of();
		}
	}

	private Response post(String url, String contentType, String body, String bearer) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Content-Type", contentType);
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		return send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build());
	}

	private Response send(HttpRequest request) {
		try {
			HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
			return new Response(response.statusCode(), response.body());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new Unavailable("Google did not respond.");
		}
		catch (IOException ex) {
			throw new Unavailable("Google could not be reached.");
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
