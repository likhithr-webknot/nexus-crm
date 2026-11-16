package com.nexus.crm.identity.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Links a user to their Google account. */
@Entity
@Table(name = "user_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {

	/** The only sign-in provider. The database only allows this value. */
	public static final String GOOGLE = "GOOGLE";

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private String provider;

	@Column(nullable = false, updatable = false)
	private String subject;

	@Column(name = "email_at_link", updatable = false, columnDefinition = "citext")
	private String emailAtLink;

	@Column(name = "linked_at", nullable = false, updatable = false)
	private Instant linkedAt;

	public UserIdentity(UUID userId, String googleId, String emailAtLink) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.provider = GOOGLE;
		this.subject = googleId;
		this.emailAtLink = emailAtLink;
		this.linkedAt = Instant.now();
	}

}
