package com.nexus.crm.identity;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts, refreshes and ends sign-in sessions. Each refresh swaps the old
 * refresh token for a new one. If an old token shows up again later, we
 * assume it was stolen and end that session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

	/** The access-token field that says which sign-in session the token belongs to. */
	public static final String SESSION_CLAIM = "sid";

	private static final SecureRandom RANDOM = new SecureRandom();

	/** The two tokens the browser keeps in cookies. */
	public record SessionTokens(String accessToken, String refreshToken) {
	}

	/** The browser and IP address a request came from. */
	public record ClientInfo(String userAgent, InetAddress ip) {

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

	private final RefreshTokenRepository refreshTokens;

	private final AppUserRepository users;

	private final JwtEncoder jwtEncoder;

	private final AuthProperties properties;

	/** Starts a new session for a user who has just signed in. */
	@Transactional
	public SessionTokens start(AppUser user, ClientInfo client) {
		return issue(user, UUID.randomUUID(), client, Instant.now());
	}

	/**
	 * Swaps a refresh token for a new pair of tokens.
	 * @return the new tokens, or empty if the token cannot be used. It returns empty instead of throwing, because
	 * throwing would undo the database change that ends a stolen session.
	 */
	@Transactional
	public Optional<SessionTokens> refresh(String rawRefreshToken, ClientInfo client) {
		RefreshToken token = this.refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken)).orElse(null);
		Instant now = Instant.now();
		if (token == null || token.isRevoked() || token.isExpired(now)) {
			return Optional.empty();
		}
		if (token.isUsed() && !token.wasUsedWithin(this.properties.refreshToken().reuseGrace(), now)) {
			// The real browser always has the newest token. An old one coming back means
			// someone else has a copy, so end the session for everyone.
			this.refreshTokens.revokeFamily(token.getFamilyId(), now);
			log.warn("Security event: refresh_token_reuse userId={} sessionId={} ip={}", token.getUserId(),
					token.getFamilyId(), client.ip());
			return Optional.empty();
		}
		AppUser user = this.users.findById(token.getUserId())
			.filter((u) -> u.getStatus() == AppUser.Status.ACTIVE)
			.orElse(null);
		if (user == null) {
			this.refreshTokens.revokeFamily(token.getFamilyId(), now);
			return Optional.empty();
		}
		// If the token was used a moment ago, this is a second browser tab refreshing at the
		// same time. That is fine: it gets its own new tokens.
		if (!token.isUsed()) {
			token.markUsed(now);
		}
		return Optional.of(issue(user, token.getFamilyId(), client, now));
	}

	/** Ends one session (sign out). */
	@Transactional
	public void end(UUID sessionId) {
		this.refreshTokens.revokeFamily(sessionId, Instant.now());
	}

	/** Ends every session of a user (sign out everywhere, or the user was disabled). */
	@Transactional
	public void endAll(UUID userId) {
		this.refreshTokens.revokeAllForUser(userId, Instant.now());
	}

	private SessionTokens issue(AppUser user, UUID sessionId, ClientInfo client, Instant now) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		this.refreshTokens.save(new RefreshToken(user.getId(), sessionId, hash(refreshToken), now,
				this.properties.refreshToken().ttl(), client.userAgent(), client.ip()));
		return new SessionTokens(accessToken(user, sessionId, now), refreshToken);
	}

	/** Creates the access token: a short-lived signed token (JWT) that proves who the user is. */
	private String accessToken(AppUser user, UUID sessionId, Instant now) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.properties.jwt().issuer())
			.subject(user.getId().toString())
			.id(UUID.randomUUID().toString())
			.issuedAt(now)
			.expiresAt(now.plus(this.properties.jwt().accessTokenTtl()))
			// Only for information. Permission checks use the role stored in the database.
			.claim("role", user.getRole().name())
			.claim(SESSION_CLAIM, sessionId.toString())
			.build();
		// The encoder signs the token and adds the ID of the key it used.
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** Hashes a token for storage. The tokens are long and random, so a plain SHA-256 hash is safe. */
	private static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
