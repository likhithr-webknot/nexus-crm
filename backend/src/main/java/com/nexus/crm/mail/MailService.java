package com.nexus.crm.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.mail.MimeMessageBuilder.Recipients;
import com.nexus.crm.modules.ModuleKind;
import com.nexus.crm.modules.ModuleRecord;
import com.nexus.crm.modules.ModuleService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Emails;
import com.nexus.crm.shared.SecretBox;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.shared.limits.RateLimit;

/**
 * A user's Google connection and the email they send through it.
 *
 * <p>Connecting is Google's OAuth consent, one feature at a time (mail, calendar or inbox). Sending takes a draft from the
 * Communications workspace and hands it to Gmail. A draft moves {@code Draft -> Sending -> Sent}; if Gmail's answer is lost
 * it becomes {@code Unknown}, and the user checks Gmail's Sent folder before deciding what to do.
 */
@Service
@RequiredArgsConstructor
public class MailService {

	private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";

	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

	private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

	private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

	/** What each feature asks Google for. Asking for one feature at a time keeps people from granting more than they use. */
	private static final Map<String, String> FEATURE_SCOPE = Map.of(
			"mail", GoogleApi.GMAIL_SEND_SCOPE,
			"calendar", GoogleApi.CALENDAR_SCOPE,
			"inbox", GoogleApi.GMAIL_READ_SCOPE);

	/** How long a person has to finish Google's consent screen. */
	private static final Duration CONSENT_VALID_FOR = Duration.ofMinutes(10);

	private static final int MAX_STATE_LENGTH = 200;

	/** A stolen or misused account could otherwise send mail as the user all day. */
	private static final RateLimit SEND_LIMIT = new RateLimit(50, Duration.ofHours(1));

	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final TransactionTemplate transactions;

	private final AuditService audit;

	private final ModuleService modules;

	private final MailBannerService banner;

	private final GoogleApi google;

	@Value("${GOOGLE_CLIENT_ID:}")
	private String clientId;

	@Value("${GOOGLE_CLIENT_SECRET:}")
	private String clientSecret;

	@Value("${NEXUS_MAIL_REDIRECT_URI:}")
	private String redirectUri;

	@Value("${NEXUS_MAIL_TOKEN_KEY:}")
	private String tokenKey;

	// ---- connection status ----

	/**
	 * What the user has connected. {@code connected} means "may send mail"; calendar and inbox have their own flags,
	 * because each is a separate permission.
	 */
	public record Status(boolean configured, boolean connected, String email, boolean calendar, boolean inbox,
			boolean needsReconnect) {
	}

	private record Connection(String email, String scopes, boolean needsReconnect) {
	}

	public Status status(UUID userId) {
		Connection connection = this.jdbc.sql("select email, scopes, needs_reconnect from mail_connection where user_id = :user")
			.param("user", userId)
			.query((rs, i) -> new Connection(rs.getString(1), rs.getString(2), rs.getBoolean(3)))
			.optional()
			.orElse(null);
		if (connection == null) {
			return new Status(configured(), false, null, false, false, false);
		}

		// A connection Google no longer accepts grants nothing until the user connects again.
		boolean usable = !connection.needsReconnect();
		return new Status(configured(),
				usable && GoogleScopes.has(connection.scopes(), GoogleApi.GMAIL_SEND_SCOPE),
				connection.email(),
				usable && GoogleScopes.has(connection.scopes(), GoogleApi.CALENDAR_SCOPE),
				usable && GoogleScopes.has(connection.scopes(), GoogleApi.GMAIL_READ_SCOPE),
				connection.needsReconnect());
	}


	/** The Google consent address for one feature. The random {@code state} ties Google's reply to this user. */
	public String connectionUrl(CurrentUser user, String feature) {
		requireConfigured();
		if (!FEATURE_SCOPE.containsKey(feature)) {
			throw invalid("Choose mail, calendar or inbox.");
		}

		byte[] random = new byte[32];
		RANDOM.nextBytes(random);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		OffsetDateTime expires = OffsetDateTime.ofInstant(Instant.now().plus(CONSENT_VALID_FOR), ZoneOffset.UTC);
		// Only a hash of the state is kept, so a database leak cannot be used to finish someone's consent.
		this.jdbc.sql("insert into mail_oauth_state (state_hash, user_id, feature, expires_at) values (:hash, :user, :feature, :expires)")
			.param("hash", hash(state))
			.param("user", user.id())
			.param("feature", feature)
			.param("expires", expires)
			.update();

		return AUTH_URL
				+ "?client_id=" + enc(this.clientId)
				+ "&redirect_uri=" + enc(this.redirectUri)
				+ "&response_type=code"
				+ "&scope=" + enc("openid email " + FEATURE_SCOPE.get(feature))
				+ "&access_type=offline&prompt=consent&include_granted_scopes=true"
				+ "&state=" + enc(state);
	}

	private record Consent(UUID userId, String feature) {
	}

	/** Finishes a Google consent and returns which feature it was for, so the caller can send the user back to the right page. */
	public String callback(String code, String state) {
		requireConfigured();
		if (code == null || state == null || state.length() > MAX_STATE_LENGTH) {
			throw invalid("Mailbox connection was cancelled or expired.");
		}
		// Deleting the row makes the state single-use.
		Consent consent = this.jdbc.sql("delete from mail_oauth_state where state_hash = :hash and expires_at > now() returning user_id, feature")
			.param("hash", hash(state))
			.query((rs, i) -> new Consent(rs.getObject(1, UUID.class), rs.getString(2)))
			.optional()
			.orElseThrow(() -> invalid("Mailbox connection was cancelled or expired."));

		Map<String, Object> token = parse(postForm(TOKEN_URL, "code=" + enc(code)
				+ "&client_id=" + enc(this.clientId)
				+ "&client_secret=" + enc(this.clientSecret)
				+ "&redirect_uri=" + enc(this.redirectUri)
				+ "&grant_type=authorization_code"));
		String accessToken = text(token.get("access_token"));
		String refreshToken = text(token.get("refresh_token"));
		if (accessToken.isEmpty() || refreshToken.isEmpty()) {
			throw invalid("Google did not grant offline mail access. Connect again.");
		}

		String googleEmail = requireSameAccount(consent.userId(), accessToken);

		// The user can untick a permission on Google's screen, so check that this feature's permission was really granted.
		String granted = text(token.get("scope"));
		if (!GoogleScopes.has(granted, FEATURE_SCOPE.get(consent.feature()))) {
			throw invalid("Google did not grant the permission this feature needs. Connect again and keep every box ticked.");
		}

		this.jdbc.sql("""
				insert into mail_connection (user_id, email, refresh_token_cipher, scopes, needs_reconnect)
				values (:user, :email, :cipher, :scopes, false)
				on conflict (user_id) do update set email = excluded.email, refresh_token_cipher = excluded.refresh_token_cipher,
				  scopes = excluded.scopes, needs_reconnect = false, connected_at = now()
				""")
			.param("user", consent.userId())
			.param("email", googleEmail)
			.param("cipher", encrypt(refreshToken))
			.param("scopes", granted)
			.update();
		turnOnFeature(consent);
		return consent.feature();
	}

	/** Only the Google account used to sign in to the CRM may be connected. Returns its email. */
	private String requireSameAccount(UUID userId, String accessToken) {
		Map<String, Object> info = parse(get(USERINFO_URL, accessToken));
		String googleEmail = text(info.get("email"));
		String crmEmail = this.jdbc.sql("select email from app_user where id = :id").param("id", userId).query(String.class).single();
		if (!googleEmail.equalsIgnoreCase(crmEmail) || !Boolean.TRUE.equals(info.get("email_verified"))) {
			throw invalid("Connect the same verified Google account used to sign in to Nexus CRM.");
		}
		return googleEmail;
	}

	/** Calendar and inbox each keep a row while sync is on for the user. Mail needs none. */
	private void turnOnFeature(Consent consent) {
		String table = switch (consent.feature()) {
			case "calendar" -> "calendar_google_sync";
			case "inbox" -> "mail_sync_state";
			default -> null;
		};
		if (table != null) {
			this.jdbc.sql("insert into " + table + " (user_id) values (:user) on conflict (user_id) do update set last_error = null")
				.param("user", consent.userId())
				.update();
		}
	}

	// ---- disconnecting ----

	/**
	 * What happened to the Google permission when a feature was switched off. Google keeps one grant per user, shared by
	 * every feature, so it can only be cancelled when the last feature is turned off.
	 */
	public record Disconnected(boolean revokedAtGoogle, boolean otherFeaturesStillOn) {
	}

	/**
	 * Stops using one feature's permission. While another feature still needs the connection, only this feature's
	 * permission is forgotten here. When it was the last one, the grant is cancelled at Google and the saved token deleted.
	 */
	public Disconnected disconnectFeature(CurrentUser user, String feature) {
		String scope = FEATURE_SCOPE.get(feature);
		if (scope == null) {
			throw invalid("Choose mail, calendar or inbox.");
		}
		record Saved(byte[] cipher, String scopes) {
		}
		Saved saved = this.jdbc.sql("select refresh_token_cipher, scopes from mail_connection where user_id = :user")
			.param("user", user.id())
			.query((rs, i) -> new Saved(rs.getBytes(1), rs.getString(2)))
			.optional()
			.orElse(null);
		if (saved == null) {
			return new Disconnected(false, false);
		}

		String remaining = GoogleScopes.without(saved.scopes(), scope);
		if (GoogleScopes.hasAnyFeature(remaining)) {
			this.transactions.executeWithoutResult((tx) -> {
				this.jdbc.sql("update mail_connection set scopes = :scopes where user_id = :user")
					.param("scopes", remaining)
					.param("user", user.id())
					.update();
				this.audit.record(user.id(), "GoogleConnection", user.id(), AuditLog.Action.UPDATE,
						AuditService.changes().field("feature", feature, null));
			});
			return new Disconnected(false, true);
		}

		// Last feature: cancel at Google first, while we still hold the token. If Google cannot be reached the saved
		// token is still deleted, and the user is told to remove the permission from their Google account by hand.
		boolean revoked = this.google.revoke(decrypt(saved.cipher()));
		this.transactions.executeWithoutResult((tx) -> {
			this.jdbc.sql("delete from mail_connection where user_id = :user").param("user", user.id()).update();
			this.audit.record(user.id(), "GoogleConnection", user.id(), AuditLog.Action.DELETE, null);
		});
		return new Disconnected(revoked, false);
	}

	// ---- sending ----

	public ModuleRecord send(CurrentUser user, UUID draftId) {
		requireConfigured();
		SEND_LIMIT.require(user.id());
		ModuleRecord draft = this.modules.get(user, ModuleKind.COMMUNICATIONS, draftId);
		requireSendable(draft);

		Status connection = status(user.id());
		String sender = connection.connected() ? connection.email() : null;
		if (sender == null) {
			throw invalid("Connect your Google Workspace mailbox before sending.");
		}
		String refreshToken = decrypt(this.jdbc.sql("select refresh_token_cipher from mail_connection where user_id = :user")
			.param("user", user.id()).query(byte[].class).single());

		claimForSending(user, draft);

		// Step 1: get a token and build the message. If this fails nothing was sent, so the draft can be retried.
		String accessToken;
		String raw;
		try {
			accessToken = refreshAccessToken(refreshToken);
			if (accessToken.isEmpty()) {
				throw invalid("The mailbox connection expired. Connect it again.");
			}
			Recipients recipients = new Recipients(draft.recipient(), draft.cc(), draft.bcc());
			String message = MimeMessageBuilder.build(sender, recipients, draft.name(), draft.description(), draftId,
					this.banner.render(user.id()));
			raw = Base64.getUrlEncoder().withoutPadding().encodeToString(message.getBytes(StandardCharsets.UTF_8));
		}
		catch (RuntimeException ex) {
			setSendStatus(draftId, "Failed");
			throw invalid("The email could not be prepared. Check the mailbox connection and try again.");
		}

		// Step 2: hand it to Gmail. From here an unclear answer means we cannot know whether it was sent.
		try {
			Map<String, Object> sent = parse(postMessage(accessToken, raw));
			String messageId = text(sent.get("id"));
			if (messageId.isEmpty()) {
				throw invalid("Gmail did not confirm delivery.");
			}
			markSent(user, draftId, messageId);
			return this.modules.get(user, ModuleKind.COMMUNICATIONS, draftId);
		}
		catch (ProviderRejected ex) {
			if (ex.status >= 400 && ex.status < 500) {
				// Gmail said no, so nothing was sent and the user can fix the cause and retry.
				setSendStatus(draftId, "Failed");
				throw invalid(rejectionMessage(ex.status, ex.reason));
			}
			setSendStatus(draftId, "Unknown");
			throw invalid("Gmail did not confirm delivery (HTTP " + ex.status + "). Check Sent mail before retrying.");
		}
		catch (RuntimeException ex) {
			setSendStatus(draftId, "Unknown");
			throw invalid("Gmail delivery could not be confirmed. Check Sent mail before contacting an admin; "
					+ "this email cannot be retried automatically.");
		}
	}

	private void requireSendable(ModuleRecord draft) {
		if (!"Draft".equals(draft.status()) && !"Failed".equals(draft.status())) {
			throw invalid("Only a draft or failed email can be sent.");
		}
		if (!Emails.isValid(draft.recipient())) {
			throw invalid("Add one valid recipient in To before sending.");
		}
		// Checked when the draft was saved, and again here because these go straight into the message headers.
		boolean copiesValid = draft.cc().stream().allMatch(Emails::isValid) && draft.bcc().stream().allMatch(Emails::isValid);
		if (!copiesValid) {
			throw invalid("Cc or Bcc has an address that is not valid. Fix it and save the draft first.");
		}
	}

	/** Moves the draft to "Sending". It only works once, so two clicks cannot send the email twice. */
	private void claimForSending(CurrentUser user, ModuleRecord draft) {
		int claimed = this.transactions.execute((tx) -> this.jdbc.sql("""
				update crm_module_record set status = 'Sending', updated_at = now(), version = version + 1
				where id = :id and owner_id = :user and status in ('Draft', 'Failed') and version = :version
				""")
			.param("id", draft.id())
			.param("user", user.id())
			.param("version", draft.version())
			.update());
		if (claimed != 1) {
			throw new ApiException(ErrorCode.VERSION_CONFLICT, "This draft changed. Reload it and try again.");
		}
	}

	private void markSent(CurrentUser user, UUID draftId, String providerMessageId) {
		this.transactions.executeWithoutResult((tx) -> {
			int updated = this.jdbc.sql("""
					update crm_module_record
					set status = 'Sent', provider_message_id = :message, sent_at = now(), updated_at = now(), version = version + 1
					where id = :id and status = 'Sending'
					""")
				.param("message", providerMessageId)
				.param("id", draftId)
				.update();
			if (updated != 1) {
				throw invalid("The send state changed unexpectedly.");
			}
			this.audit.record(user.id(), "Communication", draftId, AuditLog.Action.UPDATE, null);
		});
	}

	private void setSendStatus(UUID draftId, String status) {
		this.transactions.executeWithoutResult((tx) -> this.jdbc.sql("""
				update crm_module_record set status = :status, updated_at = now(), version = version + 1
				where id = :id and status = 'Sending'
				""")
			.param("status", status)
			.param("id", draftId)
			.update());
	}

	/** The user confirms that an email stuck in "Unknown" was not delivered, so it can be sent again. */
	@Transactional
	public ModuleRecord markUnsent(CurrentUser user, UUID id) {
		int changed = this.jdbc.sql("""
				update crm_module_record set status = 'Failed', updated_at = now(), version = version + 1
				where id = :id and kind = 'communications' and owner_id = :user and status = 'Unknown' and deleted_at is null
				""")
			.param("id", id)
			.param("user", user.id())
			.update();
		if (changed != 1) {
			throw invalid("Only your unconfirmed email can be marked as not sent.");
		}
		this.audit.record(user.id(), "Communication", id, AuditLog.Action.UPDATE, null);
		return this.modules.get(user, ModuleKind.COMMUNICATIONS, id);
	}

	/** A send that started but never finished (the server stopped, say) is flagged so the user checks Gmail. */
	@Scheduled(fixedDelay = 300000)
	public void flagInterruptedSends() {
		this.jdbc.sql("""
				update crm_module_record set status = 'Unknown', updated_at = now(), version = version + 1
				where kind = 'communications' and status = 'Sending' and updated_at < now() - interval '10 minutes'
				""").update();
	}

	/** Sends a short system email (for example an AI quota alert) from an admin's own connected mailbox. */
	public boolean sendSystemAlert(UUID adminId, String recipient, String subject, String body) {
		if (!configured()) {
			return false;
		}
		record Sender(String email, byte[] cipher, String scopes) {
		}
		Sender sender = this.jdbc.sql("""
				select email, refresh_token_cipher, scopes from mail_connection
				where user_id = :user and needs_reconnect = false
				""")
			.param("user", adminId)
			.query((rs, i) -> new Sender(rs.getString(1), rs.getBytes(2), rs.getString(3)))
			.optional()
			.orElse(null);
		if (sender == null || !GoogleScopes.has(sender.scopes(), GoogleApi.GMAIL_SEND_SCOPE)) {
			return false;
		}
		String accessToken = refreshAccessToken(decrypt(sender.cipher()));
		if (accessToken.isEmpty()) {
			return false;
		}

		String message = MimeMessageBuilder.build(sender.email(), new Recipients(recipient, List.of(), List.of()), subject, body,
				UUID.randomUUID(), null);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(message.getBytes(StandardCharsets.UTF_8));
		return !text(parse(postMessage(accessToken, raw)).get("id")).isEmpty();
	}

	static String rejectionMessage(int status, String reason) {
		if (status == 403 && "PERMISSION_DENIED".equalsIgnoreCase(reason)) {
			return "Gmail rejected this send with PERMISSION_DENIED. Reconnect the mailbox to refresh its gmail.send permission. "
					+ "If it still fails, ask your Google Cloud admin to enable the Gmail API and your Workspace admin "
					+ "to allow this OAuth app and Gmail send scope. Google rejected this request; no email was sent.";
		}
		return "Gmail rejected the email (HTTP " + status + ", " + reason
				+ "). Check Gmail API access and the mailbox connection, then try again.";
	}

	// ---- talking to Google ----

	/** Thrown when Google answers with an error status. {@code reason} is a short code such as PERMISSION_DENIED. */
	private static final class ProviderRejected extends RuntimeException {

		final int status;

		final String reason;

		ProviderRejected(int status, String reason) {
			super("Google answered HTTP " + status);
			this.status = status;
			this.reason = reason;
		}

	}

	/** Trades a stored refresh token for a short-lived access token. Empty when Google did not give one. */
	private String refreshAccessToken(String refreshToken) {
		String body = postForm(TOKEN_URL, "refresh_token=" + enc(refreshToken)
				+ "&client_id=" + enc(this.clientId)
				+ "&client_secret=" + enc(this.clientSecret)
				+ "&grant_type=refresh_token");
		return text(parse(body).get("access_token"));
	}

	private String postMessage(String accessToken, String rawMessage) {
		String body = this.json.writeValueAsString(Map.of("raw", rawMessage));
		return answer(() -> this.google.call("POST", SEND_URL, accessToken, body));
	}

	private String postForm(String url, String form) {
		return answer(() -> this.google.postForm(url, form));
	}

	private String get(String url, String accessToken) {
		return answer(() -> this.google.call("GET", url, accessToken, null));
	}

	/** The body of a successful answer. A refusal becomes {@link ProviderRejected}; no answer at all becomes a plain error. */
	private String answer(Supplier<GoogleApi.Response> request) {
		GoogleApi.Response response;
		try {
			response = request.get();
		}
		catch (GoogleApi.Unavailable ex) {
			throw invalid("The mail provider did not respond.");
		}
		if (!response.ok()) {
			throw new ProviderRejected(response.status(), reasonOf(response.body()));
		}
		return response.body();
	}

	/** A short, safe code from Google's error body. Anything unexpected is reduced to a fixed word, never shown as-is. */
	@SuppressWarnings("unchecked")
	private String reasonOf(String body) {
		Map<String, Object> parsed = this.google.parse(body);
		Map<String, Object> error = (parsed.get("error") instanceof Map<?, ?> value) ? (Map<String, Object>) value : Map.of();
		String reason = text(error.get("status"));
		if (reason.isBlank()) {
			reason = text(error.get("code"));
		}
		return reason.matches("[A-Z_]{2,60}") ? reason : "provider_rejected";
	}

	private Map<String, Object> parse(String body) {
		return this.google.parse(body);
	}

	// ---- configuration and small helpers ----

	private boolean configured() {
		return !this.clientId.isBlank() && !this.clientSecret.isBlank() && !this.redirectUri.isBlank()
				&& SecretBox.of(this.tokenKey, null) != null;
	}

	private void requireConfigured() {
		if (!configured()) {
			throw invalid("Mail connection is not configured. Ask an admin to set it up.");
		}
	}

	/** The refresh token is stored encrypted. {@code NEXUS_MAIL_TOKEN_KEY} may list several keys, newest first, to replace a key. */
	private byte[] encrypt(String value) {
		return mailKeys().encrypt(value);
	}

	private String decrypt(byte[] stored) {
		return mailKeys().decrypt(stored);
	}

	private SecretBox mailKeys() {
		SecretBox box = SecretBox.of(this.tokenKey, null);
		if (box == null) {
			throw new IllegalStateException("NEXUS_MAIL_TOKEN_KEY is not a valid key.");
		}
		return box;
	}

	private static byte[] hash(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String text(Object value) {
		return (value instanceof String s) ? s : "";
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.INVALID_STATE, message);
	}

}
