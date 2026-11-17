package com.nexus.crm.identity.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;

public record UserResponse(
	UUID id,
	String email,
	String displayName,
	String jobTitle,
	String avatarUrl,
	Role role,
	AppUser.Status status,
	UUID teamId,
	Instant lastLoginAt
) {

	/** The same person as other staff may see them: no sign-in activity. */
	public UserResponse withoutActivity() {
		return new UserResponse(id, email, displayName, jobTitle, avatarUrl, role, status, teamId, null);
	}

	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getJobTitle(), user.getAvatarUrl(),
				user.getRole(), user.getStatus(), user.getTeamId(), user.getLastLoginAt());
	}

}
