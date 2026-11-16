package com.nexus.crm.identity.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.shared.Role;

public record InviteUserRequest(
	@NotBlank @Email @Size(max = 254) String email,
	@NotBlank @Size(max = 120) String displayName,
	@NotNull Role role
) {
}
