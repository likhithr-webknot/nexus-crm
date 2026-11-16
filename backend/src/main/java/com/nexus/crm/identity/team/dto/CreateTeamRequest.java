package com.nexus.crm.identity.team.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
	@NotBlank @Size(max = 80) String name,
	UUID managerId
) {
}
