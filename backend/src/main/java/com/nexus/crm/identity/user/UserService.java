package com.nexus.crm.identity.user;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nexus.crm.identity.user.event.UserDisabled;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** Invites users, changes their role and disables them. The controllers decide who may call this. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	/** Remembers each user's role for 60 seconds. */
	private final Cache<UUID, Optional<Role>> activeRoles = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(60))
		.maximumSize(10_000)
		.build();

	private final AppUserRepository users;

	private final ApplicationEventPublisher events;

	@Transactional(readOnly = true)
	public AppUser get(UUID id) {
		return this.users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
	}

	@Transactional(readOnly = true)
	public List<AppUser> list() {
		return this.users.findAll(Sort.by("displayName", "email"));
	}

	/** Display names by user ID, so lists can show "Sarah Chen" instead of an ID. Unknown IDs are left out. */
	@Transactional(readOnly = true)
	public Map<UUID, String> displayNames(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return this.users.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
	}

	/** Whose records the user may see: an admin sees everyone's (empty result), a manager sees their own and their teams' members', and a rep sees only their own. */
	@Transactional(readOnly = true)
	public Optional<Set<UUID>> visibleOwnerIds(CurrentUser user) {
		return switch (user.role()) {
			case ADMIN -> Optional.empty();
			case MANAGER -> {
				Set<UUID> ids = new HashSet<>(this.users.findIdsInTeamsLedBy(user.id()));
				ids.add(user.id());
				yield Optional.of(ids);
			}
			case REP -> Optional.of(Set.of(user.id()));
		};
	}

	@Transactional
	public AppUser rename(UUID id, String displayName) {
		AppUser user = get(id);
		user.rename(displayName.strip());
		return user;
	}

	/** Removes a user who was invited but never signed in, for example because the email was mistyped. */
	@Transactional
	public void delete(UUID id, UUID actorId) {
		AppUser user = get(id);
		if (id.equals(actorId)) {
			throw new ApiException(ErrorCode.INVALID_STATE, "You cannot remove your own account.");
		}
		if (user.getStatus() != AppUser.Status.INVITED || user.getLastLoginAt() != null) {
			throw new ApiException(ErrorCode.INVALID_STATE,
					"Only someone who was invited and never signed in can be removed. Disable the account instead.");
		}
		try {
			this.users.delete(user);
			this.users.flush();
		}
		catch (org.springframework.dao.DataIntegrityViolationException ex) {
			throw new ApiException(ErrorCode.INVALID_STATE, "This user is referred to by other records. Disable the account instead.");
		}
		this.activeRoles.invalidate(id);
		log.info("User removed: userId={} by={}", id, actorId);
	}

	/** Checks that the user may give a record to this person. Returns the ID so it can be used inline. */
	@Transactional(readOnly = true)
	public UUID requireCanAssignTo(CurrentUser user, UUID ownerId) {
		AppUser target = this.users.findById(ownerId)
			.filter((u) -> u.getStatus() == AppUser.Status.ACTIVE)
			.orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "That person is not an active user."));
		boolean allowed = visibleOwnerIds(user).map((ids) -> ids.contains(target.getId())).orElse(true);
		if (!allowed) {
			throw new ApiException(ErrorCode.FORBIDDEN, "You can't assign records to this person.");
		}
		return target.getId();
	}

	/** Users that records can be given to: active people the signed-in user is allowed to assign to. */
	@Transactional(readOnly = true)
	public List<AppUser> assignable(CurrentUser user) {
		Optional<Set<UUID>> visible = visibleOwnerIds(user);
		return this.users.findAll(Sort.by("displayName"))
			.stream()
			.filter((u) -> u.getStatus() == AppUser.Status.ACTIVE)
			.filter((u) -> visible.map((ids) -> ids.contains(u.getId())).orElse(true))
			.toList();
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

	/** Changes a user's role, status or both. */
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
			// The auth module listens and ends the user's sessions, so this class does not depend on it.
			this.events.publishEvent(new UserDisabled(id));
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
	 * Invites this email as an admin when there are no users yet, so a new install has someone who can invite
	 * the others. Does nothing once any user exists.
	 */
	@Transactional
	public void inviteFirstAdmin(String email) {
		if (!StringUtils.hasText(email) || this.users.count() > 0) {
			return;
		}
		String name = email.strip().split("@")[0];
		AppUser admin = this.users.save(AppUser.invite(email, name, Role.ADMIN, null));
		log.info("Invited the first admin, userId={}", admin.getId());
	}

}
