package com.nexus.crm.identity.auth;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A refresh token. We store only a hash of it, so a leaked database does not give anyone a working token. */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "family_id", nullable = false, updatable = false)
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private byte[] tokenHash;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "user_agent", updatable = false)
	private String userAgent;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(updatable = false)
	private InetAddress ip;

	public RefreshToken(UUID userId, UUID familyId, byte[] tokenHash, Instant issuedAt, Duration ttl, String userAgent,
			InetAddress ip) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.issuedAt = issuedAt;
		this.expiresAt = issuedAt.plus(ttl);
		this.userAgent = userAgent;
		this.ip = ip;
	}

	public boolean isRevoked() {
		return this.revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

	public boolean isUsed() {
		return this.usedAt != null;
	}

	/** True if the token was already used, but only a moment ago. */
	public boolean wasUsedWithin(Duration grace, Instant now) {
		return this.usedAt != null && !now.isAfter(this.usedAt.plus(grace));
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

}
