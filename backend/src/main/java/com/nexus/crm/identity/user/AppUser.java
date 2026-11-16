package com.nexus.crm.identity.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.nexus.crm.shared.Role;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

	/** A user's status. The database only allows these same values. */
	public enum Status {

		/** Invited by an admin, but has never signed in. */
		INVITED, ACTIVE, DISABLED

	}

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false, columnDefinition = "citext")
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "job_title")
	private String jobTitle;

	@Column(name = "avatar_url")
	private String avatarUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "team_id")
	private UUID teamId;

	@Column(name = "invited_by", updatable = false)
	private UUID invitedBy;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private int version;

	/** Creates an invited user. This is the only way a user is created. */
	public static AppUser invite(String email, String displayName, Role role, UUID invitedBy) {
		AppUser user = new AppUser();
		user.id = UUID.randomUUID();
		user.email = normalizeEmail(email);
		user.displayName = displayName;
		user.role = role;
		user.status = Status.INVITED;
		user.invitedBy = invitedBy;
		user.createdAt = Instant.now();
		user.updatedAt = user.createdAt;
		return user;
	}

	/** Trims and lower-cases an email, so the same address always matches. */
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	/** Call after a successful sign-in. An invited user becomes active the first time. */
	public void signedIn(String avatarUrl, Instant at) {
		if (this.status == Status.INVITED) {
			this.status = Status.ACTIVE;
		}
		this.avatarUrl = avatarUrl;
		this.lastLoginAt = at;
	}

	public void rename(String displayName) {
		this.displayName = displayName;
	}

	/** A blank job title is stored as none. */
	public void editProfile(String displayName, String jobTitle) {
		this.displayName = displayName.strip();
		this.jobTitle = (jobTitle == null || jobTitle.isBlank()) ? null : jobTitle.strip();
	}

	public void setTeamId(UUID teamId) {
		this.teamId = teamId;
	}

	public void changeRole(Role role) {
		this.role = role;
	}

	public void disable() {
		this.status = Status.DISABLED;
	}

	/** Re-enables a disabled user: active if they have signed in before, otherwise invited. */
	public void enable() {
		if (this.status == Status.DISABLED) {
			this.status = (this.lastLoginAt != null) ? Status.ACTIVE : Status.INVITED;
		}
	}

	@PreUpdate
	void touch() {
		this.updatedAt = Instant.now();
	}

}
