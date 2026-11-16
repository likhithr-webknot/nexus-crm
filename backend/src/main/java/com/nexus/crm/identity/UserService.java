package com.nexus.crm.identity;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nexus.crm.shared.ApiException;
import com.nexus.crm.shared.ErrorCode;
import com.nexus.crm.shared.Role;

/** Invites users, changes their role and disables them. The controllers decide who may call this. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	/**
	 * Remembers each user's role for 60 seconds. Every request checks it, and
	 * this saves a database query each time. An empty value means "not active".
	 */
	private final Cache<UUID, Optional<Role>> activeRoles = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(60))
		.maximumSize(10_000)
		.build();

	private final AppUserRepository users;

	private final SessionService sessions;

	private final AuthProperties properties;

	@Transactional(readOnly = true)
	public AppUser get(UUID id) {
		return this.users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
	}

	@Transactional(readOnly = true)
	public List<AppUser> list() {
		return this.users.findAll(Sort.by("displayName", "email"));
	}

	/** Returns the user's role, or empty if the user is missing or not active. */
	public Optional<Role> activeRole(UUID userId) {
		return this.activeRoles.get(userId,
				(id) -> this.users.findById(id)
					.filter((user) -> user.getStatus() == AppUser.Status.ACTIVE)
					.map(AppUser::getRole));
	}

	@Transactional
	public AppUser invite(String email, String displayName, Role role, UUID invitedBy) {
		String normalized = AppUser.normalizeEmail(email);
		if (this.users.existsByEmail(normalized)) {
			throw new ApiException(ErrorCode.DUPLICATE, "A user with this email already exists.");
		}
		AppUser user = this.users.save(AppUser.invite(normalized, displayName.strip(), role, invitedBy));
		log.info("User invited: userId={} role={} invitedBy={}", user.getId(), role, invitedBy);
		return user;
	}

	/**
	 * Changes a user's role, status or both. Pass null for anything that should stay the same.
	 * @param status DISABLED to disable the user, ACTIVE to enable them again
	 */
	@Transactional
	public AppUser update(UUID id, Role role, AppUser.Status status, UUID actorId) {
		AppUser user = get(id);
		if (id.equals(actorId)) {
			// Admins cannot change themselves, so there is always at least one admin left.
			throw new ApiException(ErrorCode.INVALID_STATE, "You cannot change your own role or status.");
		}
		if (role != null) {
			user.changeRole(role);
		}
		if (status == AppUser.Status.DISABLED) {
			user.disable();
			this.sessions.endAll(id);
		}
		else if (status == AppUser.Status.ACTIVE) {
			user.enable();
		}
		else if (status != null) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Status can only be set to ACTIVE or DISABLED.");
		}
		// Forget the remembered role, so the change applies on the user's next request.
		this.activeRoles.invalidate(id);
		log.info("User updated: userId={} role={} status={} by={}", id, user.getRole(), user.getStatus(), actorId);
		return user;
	}

	/**
	 * Creates the first admin when the app starts. Only admins can invite
	 * people, so a brand-new install needs one to begin with. If there are no
	 * users yet and {@code nexus.auth.bootstrap-admin-email} is set, that email
	 * is invited as an admin. Once any user exists this does nothing.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void inviteFirstAdmin() {
		String email = this.properties.bootstrapAdminEmail();
		if (!StringUtils.hasText(email) || this.users.count() > 0) {
			return;
		}
		String name = email.strip().split("@")[0];
		AppUser admin = this.users.save(AppUser.invite(email, name, Role.ADMIN, null));
		log.info("Invited the first admin, userId={}", admin.getId());
	}

}
