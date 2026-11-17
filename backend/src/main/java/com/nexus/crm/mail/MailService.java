package com.nexus.crm.mail;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.modules.ModuleKind;
import com.nexus.crm.modules.ModuleRecord;
import com.nexus.crm.modules.ModuleService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Emails;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class MailService {
	private static final String SCOPE = "openid email https://www.googleapis.com/auth/gmail.send";
	private static final SecureRandom RANDOM = new SecureRandom();
	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final TransactionTemplate transactions;
	private final AuditService audit;
	private final ModuleService modules;
	private final MailBannerService banner;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	@Value("${GOOGLE_CLIENT_ID:}") private String clientId;
	@Value("${GOOGLE_CLIENT_SECRET:}") private String clientSecret;
	@Value("${NEXUS_MAIL_REDIRECT_URI:}") private String redirectUri;
	@Value("${NEXUS_MAIL_TOKEN_KEY:}") private String tokenKey;

	public record Status(boolean configured, boolean connected, String email) { }

	public Status status(UUID userId) {
		String email = jdbc.sql("select email from mail_connection where user_id=:user")
			.param("user", userId).query(String.class).optional().orElse(null);
		return new Status(configured(), email != null, email);
	}

	public String connectionUrl(CurrentUser user) {
		requireConfigured();
		byte[] random = new byte[32];
		RANDOM.nextBytes(random);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		jdbc.sql("insert into mail_oauth_state (state_hash,user_id,expires_at) values (:hash,:user,:expiry)")
			.param("hash", hash(state)).param("user", user.id()).param("expiry", java.time.OffsetDateTime.ofInstant(Instant.now().plusSeconds(600), java.time.ZoneOffset.UTC)).update();
		return "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + enc(clientId)
			+ "&redirect_uri=" + enc(redirectUri) + "&response_type=code&scope=" + enc(SCOPE)
			+ "&access_type=offline&prompt=consent&include_granted_scopes=true&state=" + enc(state);
	}

	public void callback(String code, String state) {
		requireConfigured();
		if (code == null || state == null || state.length() > 200) throw invalid("Mailbox connection was cancelled or expired.");
		UUID userId = jdbc.sql("delete from mail_oauth_state where state_hash=:hash and expires_at>now() returning user_id")
			.param("hash", hash(state)).query(UUID.class).optional()
			.orElseThrow(() -> invalid("Mailbox connection was cancelled or expired."));
		Map<String, Object> token = postForm("https://oauth2.googleapis.com/token", "code=" + enc(code)
			+ "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret)
			+ "&redirect_uri=" + enc(redirectUri) + "&grant_type=authorization_code");
		String access = string(token.get("access_token"));
		String refresh = string(token.get("refresh_token"));
		if (access.isEmpty() || refresh.isEmpty()) throw invalid("Google did not grant offline mail access. Connect again.");
		Map<String, Object> info = getJson("https://openidconnect.googleapis.com/v1/userinfo", access);
		String email = string(info.get("email"));
		String expected = jdbc.sql("select email from app_user where id=:id").param("id", userId)
			.query(String.class).single();
		if (!email.equalsIgnoreCase(expected) || !Boolean.TRUE.equals(info.get("email_verified")))
			throw invalid("Connect the same verified Google account used to sign in to Nexus CRM.");
		jdbc.sql("insert into mail_connection (user_id,email,refresh_token_cipher) values (:user,:email,:cipher) "
				+ "on conflict (user_id) do update set email=excluded.email,refresh_token_cipher=excluded.refresh_token_cipher,connected_at=now()")
			.param("user", userId).param("email", email).param("cipher", encrypt(refresh)).update();
	}

	public ModuleRecord send(CurrentUser user, UUID id) {
		requireConfigured();
		ModuleRecord draft = modules.get(user, ModuleKind.COMMUNICATIONS, id);
		requireSendable(draft);
		String sender = status(user.id()).email();
		if (sender == null) throw invalid("Connect your Google Workspace mailbox before sending.");
		byte[] encrypted = jdbc.sql("select refresh_token_cipher from mail_connection where user_id=:user")
			.param("user", user.id()).query(byte[].class).single();
		String refresh = decrypt(encrypted);
		claimForSending(user, draft);
		String access;
		String raw;
		try {
			Map<String, Object> token = postForm("https://oauth2.googleapis.com/token", "refresh_token=" + enc(refresh)
				+ "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret) + "&grant_type=refresh_token");
			access = string(token.get("access_token"));
			if (access.isEmpty()) throw invalid("The mailbox connection expired. Connect it again.");
			Recipients recipients = new Recipients(draft.recipient(), draft.cc(), draft.bcc());
			String mime = personalizedMime(user.id(), sender, recipients, draft.name(), draft.description(), id);
			raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
		} catch (RuntimeException ex) {
			setSendStatus(id, "Failed");
			throw invalid("The email could not be prepared. Check the mailbox connection and try again.");
		}
		try {
			Map<String, Object> sent = postJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", access,
				json.writeValueAsString(Map.of("raw", raw)));
			String messageId = string(sent.get("id"));
			if (messageId.isEmpty()) throw invalid("Gmail did not confirm delivery.");
			transactions.executeWithoutResult(tx -> {
				int updated = jdbc.sql("update crm_module_record set status='Sent',provider_message_id=:message,sent_at=now(),updated_at=now(),version=version+1 where id=:id and status='Sending'")
					.param("message", messageId).param("id", id).update();
				if (updated != 1) throw invalid("The send state changed unexpectedly.");
				audit.record(user.id(), "Communication", id, AuditLog.Action.UPDATE, null);
			});
			return modules.get(user, ModuleKind.COMMUNICATIONS, id);
		} catch (ProviderRejected ex) {
			if (ex.status >= 400 && ex.status < 500) {
				setSendStatus(id, "Failed");
				throw invalid(rejectionMessage(ex.status, ex.reason));
			}
			setSendStatus(id, "Unknown");
			throw invalid("Gmail did not confirm delivery (HTTP " + ex.status + "). Check Sent mail before retrying.");
		} catch (RuntimeException ex) {
			setSendStatus(id, "Unknown");
			throw invalid("Gmail delivery could not be confirmed. Check Sent mail before contacting an admin; this email cannot be retried automatically.");
		}
	}

	private void requireSendable(ModuleRecord draft) {
		if (!"Draft".equals(draft.status()) && !"Failed".equals(draft.status()))
			throw invalid("Only a draft or failed email can be sent.");
		if (!Emails.isValid(draft.recipient())) throw invalid("Add one valid recipient in To before sending.");
		// Checked when the draft was saved, and again here because these go straight into the message headers.
		boolean copiesValid = draft.cc().stream().allMatch(Emails::isValid) && draft.bcc().stream().allMatch(Emails::isValid);
		if (!copiesValid) throw invalid("Cc or Bcc has an address that is not valid. Fix it and save the draft first.");
	}

	/** Moves the draft to "Sending". It only works once, so two clicks cannot send the email twice. */
	private void claimForSending(CurrentUser user, ModuleRecord draft) {
		int claimed = transactions.execute(tx -> jdbc.sql("update crm_module_record set status='Sending',updated_at=now(),version=version+1 "
				+ "where id=:id and owner_id=:user and status in ('Draft','Failed') and version=:version")
			.param("id", draft.id()).param("user", user.id()).param("version", draft.version()).update());
		if (claimed != 1) throw new ApiException(ErrorCode.VERSION_CONFLICT, "This draft changed. Reload it and try again.");
	}

	@Transactional
	public ModuleRecord markUnsent(CurrentUser user, UUID id) {
		int changed = jdbc.sql("update crm_module_record set status='Failed',updated_at=now(),version=version+1 "
			+ "where id=:id and kind='communications' and owner_id=:user and status='Unknown' and deleted_at is null")
			.param("id", id).param("user", user.id()).update();
		if (changed != 1) throw invalid("Only your unconfirmed email can be marked as not sent.");
		audit.record(user.id(), "Communication", id, AuditLog.Action.UPDATE, null);
		return modules.get(user, ModuleKind.COMMUNICATIONS, id);
	}

	@Scheduled(fixedDelay = 300000)
	public void flagInterruptedSends() {
		jdbc.sql("update crm_module_record set status='Unknown',updated_at=now(),version=version+1 "
			+ "where kind='communications' and status='Sending' and updated_at<now()-interval '10 minutes'").update();
	}

	private void setSendStatus(UUID id, String status) {
		transactions.executeWithoutResult(tx -> jdbc.sql("update crm_module_record set status=:status,updated_at=now(),version=version+1 where id=:id and status='Sending'")
			.param("status", status).param("id", id).update());
	}

	public boolean sendSystemAlert(UUID adminId, String recipient, String subject, String body) {
		if (!configured()) return false;
		var connection = jdbc.sql("select email,refresh_token_cipher from mail_connection where user_id=:user")
			.param("user", adminId).query((rs, row) -> Map.<String, Object>of("email", rs.getString("email"),
				"cipher", rs.getBytes("refresh_token_cipher"))).optional();
		if (connection.isEmpty()) return false;
		String sender = (String) connection.get().get("email");
		String refresh = decrypt((byte[]) connection.get().get("cipher"));
		Map<String, Object> token = postForm("https://oauth2.googleapis.com/token", "refresh_token=" + enc(refresh)
			+ "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret) + "&grant_type=refresh_token");
		String access = string(token.get("access_token"));
		if (access.isEmpty()) return false;
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime(sender, new Recipients(recipient, List.of(), List.of()), subject, body, UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
		return !string(postJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", access,
			json.writeValueAsString(Map.of("raw", raw))).get("id")).isEmpty();
	}

	private boolean configured() {
		if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank() || tokenKey.isBlank()) return false;
		try { return Base64.getDecoder().decode(tokenKey).length == 32; }
		catch (IllegalArgumentException e) { return false; }
	}
	private void requireConfigured() { if (!configured()) throw invalid("Mail connection is not configured. Ask an admin to set it up."); }
	private byte[] hash(String value) {
		try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
		catch (Exception e) { throw new IllegalStateException(e); }
	}
	private byte[] encrypt(String value) {
		try {
			byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(tokenKey), "AES"), new GCMParameterSpec(128, nonce));
			byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
		} catch (Exception e) { throw new IllegalStateException("Mail encryption failed.", e); }
	}
	private String decrypt(byte[] stored) {
		try {
			ByteBuffer data = ByteBuffer.wrap(stored);
			byte[] nonce = new byte[12]; data.get(nonce);
			byte[] ciphertext = new byte[data.remaining()]; data.get(ciphertext);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(tokenKey), "AES"), new GCMParameterSpec(128, nonce));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (Exception e) { throw new IllegalStateException("Mail connection could not be read.", e); }
	}
	/** Who a message goes to. Each address passed {@link Emails#isValid}, so none can add a header line or a second recipient. */
	record Recipients(String to, List<String> cc, List<String> bcc) { }

	/**
	 * The To, Cc and Bcc lines. Each address goes on its own folded line, so a long list stays under the 998 character
	 * limit. Gmail delivers to Bcc and removes that header from the copies the others receive.
	 */
	static String addressHeaders(Recipients recipients) {
		StringBuilder headers = new StringBuilder("To: ").append(recipients.to()).append("\r\n");
		if (!recipients.cc().isEmpty()) headers.append("Cc: ").append(String.join(",\r\n ", recipients.cc())).append("\r\n");
		if (!recipients.bcc().isEmpty()) headers.append("Bcc: ").append(String.join(",\r\n ", recipients.bcc())).append("\r\n");
		return headers.toString();
	}

	/** A plain HTML message. */
	private String mime(String from, Recipients to, String subject, String body, UUID id) {
		return commonHeaders(from, to, subject, id)
			+ "Content-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
			+ wrapBase64(html(body, null).getBytes(StandardCharsets.UTF_8));
	}

	/** The message with the sender's signature image inside it, or a plain one when they have none. */
	private String personalizedMime(UUID senderId, String from, Recipients to, String subject, String body, UUID id) {
		MailBannerService.Rendered signature = banner.render(senderId);
		if (signature == null) return mime(from, to, subject, body, id);
		String boundary = "nexus_" + id.toString().replace("-", "");
		String html = html(body, signature.alt());
		return commonHeaders(from, to, subject, id)
			+ "Content-Type: multipart/related; boundary=\"" + boundary + "\"\r\n\r\n"
			+ "--" + boundary + "\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
			+ wrapBase64(html.getBytes(StandardCharsets.UTF_8))
			+ "\r\n--" + boundary + "\r\nContent-Type: image/png\r\nContent-ID: <nexus-banner>\r\n"
			+ "Content-Disposition: inline; filename=\"banner.png\"\r\nContent-Transfer-Encoding: base64\r\n\r\n"
			+ wrapBase64(signature.png())
			+ "\r\n--" + boundary + "--\r\n";
	}

	private String commonHeaders(String from, Recipients to, String subject, UUID id) {
		String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
		return "From: " + from + "\r\n" + addressHeaders(to) + "Subject: " + encodedSubject
			+ "\r\nMessage-ID: <" + id + "@nexus-crm.local>\r\nMIME-Version: 1.0\r\n";
	}

	/** Base64 in lines of 76 characters, as mail requires. */
	private String wrapBase64(byte[] bytes) {
		return Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(bytes);
	}

	/**
	 * The message as HTML: readable text in a 600px column, then the signature image when there is one.
	 * No text colours are set, so a mail client's dark mode can restyle the text itself.
	 */
	private String html(String body, String signatureAlt) {
		String signature = signatureAlt == null ? "" : "<div style=\"margin-top:28px\"><img src=\"cid:nexus-banner\" width=\"" + MailBannerService.SHOWN_WIDTH
			+ "\" alt=\"" + escape(signatureAlt) + "\" style=\"display:block;width:100%;max-width:" + MailBannerService.SHOWN_WIDTH
			+ "px;height:auto;border:0;border-radius:8px\"></div>";
		return "<!doctype html><html><body style=\"margin:0;padding:0\"><div style=\"max-width:600px;"
			+ "font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.6\">"
			+ escape(body).replace("\n", "<br>") + signature + "</div></body></html>";
	}
	private String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
	private Map<String, Object> postForm(String url, String form) {
		return exchange(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build());
	}
	private Map<String, Object> postJson(String url, String bearer, String body) {
		return exchange(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
			.header("Authorization", "Bearer " + bearer).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)).build());
	}
	private Map<String, Object> getJson(String url, String bearer) {
		return exchange(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
			.header("Authorization", "Bearer " + bearer).GET().build());
	}
	private static final class ProviderRejected extends RuntimeException {
		final int status;
		final String reason;
		ProviderRejected(int status, String reason) { this.status = status; this.reason = reason; }
	}

	static String rejectionMessage(int status, String reason) {
		if (status == 403 && "PERMISSION_DENIED".equalsIgnoreCase(reason)) {
			return "Gmail rejected this send with PERMISSION_DENIED. Reconnect the mailbox to refresh its gmail.send permission. "
				+ "If it still fails, ask your Google Cloud admin to enable the Gmail API and your Workspace admin to allow this OAuth app and Gmail send scope. "
				+ "Google rejected this request; no email was sent.";
		}
		return "Gmail rejected the email (HTTP " + status + ", " + reason + "). Check Gmail API access and the mailbox connection, then try again.";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> exchange(HttpRequest request) {
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ProviderRejected(response.statusCode(), providerReason(response.body()));
			return json.readValue(response.body(), Map.class);
		} catch (InterruptedException e) { Thread.currentThread().interrupt(); throw invalid("The mail provider did not respond."); }
		catch (java.io.IOException e) { throw invalid("The mail provider did not respond."); }
	}
	@SuppressWarnings("unchecked")
	private String providerReason(String body) {
		try {
			Map<String, Object> parsed = json.readValue(body, Map.class);
			Map<String, Object> error = parsed.get("error") instanceof Map<?,?> value ? (Map<String, Object>) value : Map.of();
			String reason = string(error.get("status"));
			if (reason.isBlank()) reason = string(error.get("code"));
			return reason.matches("[A-Z_]{2,60}") ? reason : "provider_rejected";
		} catch (RuntimeException ignored) { return "provider_rejected"; }
	}
	private String string(Object value) { return value instanceof String s ? s : ""; }
	private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
	private ApiException invalid(String message) { return new ApiException(ErrorCode.INVALID_STATE, message); }
}
