package com.nexus.crm.identity.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.identity.auth.dto.ClientInfo;
import com.nexus.crm.identity.auth.dto.SessionTokens;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.AppUserRepository;

/** Starts, refreshes and ends sign-in sessions. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

	/** The access-token field that says which sign-in session the token belongs to. */
	public static final String SESSION_CLAIM = "sid";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokens;

	private final AppUserRepository users;

	private final JwtEncoder jwtEncoder;

	private final AuthProperties properties;

	/** Which sessions are still alive. Kept for a few seconds so a request does not need a database query. */
	private final Cache<UUID, Boolean> liveSessions = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(15))
		.maximumSize(50_000)
		.build();

	/** Starts a new session for a user who has just signed in. */
	@Transactional
	public SessionTokens start(AppUser user, ClientInfo client) {
		return issue(user, UUID.randomUUID(), client, Instant.now());
	}

	/** Swaps a refresh token for a new pair of tokens. */
	@Transactional
	public Optional<SessionTokens> refresh(String rawRefreshToken, ClientInfo client) {
		RefreshToken token = this.refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken)).orElse(null);
		Instant now = Instant.now();
		if (token == null || token.isRevoked() || token.isExpired(now)) {
			return Optional.empty();
		}
		if (token.isUsed() && !token.wasUsedWithin(this.properties.refreshToken().reuseGrace(), now)) {
			// The real browser always has the newest token.
			this.refreshTokens.revokeFamily(token.getFamilyId(), now);
			this.liveSessions.invalidate(token.getFamilyId());
			log.warn("Security event: refresh_token_reuse userId={} sessionId={} ip={}", token.getUserId(),
					token.getFamilyId(), client.ip());
			return Optional.empty();
		}
		AppUser user = this.users.findById(token.getUserId())
			.filter((u) -> u.getStatus() == AppUser.Status.ACTIVE)
			.orElse(null);
		if (user == null) {
			this.refreshTokens.revokeFamily(token.getFamilyId(), now);
			this.liveSessions.invalidate(token.getFamilyId());
			return Optional.empty();
		}
		// If the token was used a moment ago, this is a second browser tab refreshing at the same time.
		if (!token.isUsed()) {
			token.markUsed(now);
		}
		return Optional.of(issue(user, token.getFamilyId(), client, now));
	}

	/**
	 * Whether the sign-in session an access token belongs to has not been ended. Without this check, signing out would
	 * only stop new tokens: the access token already issued would keep working until it expires. On other servers it
	 * can take up to 15 seconds to notice.
	 */
	public boolean isLive(UUID sessionId) {
		return this.liveSessions.get(sessionId, (id) -> this.refreshTokens.existsLive(id, Instant.now()));
	}

	/** Ends one session (sign out). */
	@Transactional
	public void end(UUID sessionId) {
		this.refreshTokens.revokeFamily(sessionId, Instant.now());
		this.liveSessions.invalidate(sessionId);
	}

	/** Ends every session of a user (sign out everywhere, or the user was disabled). */
	@Transactional
	public void endAll(UUID userId) {
		this.refreshTokens.revokeAllForUser(userId, Instant.now());
		this.liveSessions.invalidateAll();
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

	/** A disabled user is signed out of every device at once. */
	@org.springframework.context.event.EventListener
	void onUserDisabled(com.nexus.crm.identity.user.event.UserDisabled event) {
		endAll(event.userId());
	}

}
