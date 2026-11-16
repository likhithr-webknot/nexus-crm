package com.nexus.crm.identity.user.dto;

import jakarta.validation.constraints.Size;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;

/** Both fields are optional. A field that is left out is not changed. */
public record UpdateUserRequest(
	Role role,
	AppUser.Status status,
	@Size(min = 1, max = 120) String displayName
) {
}
