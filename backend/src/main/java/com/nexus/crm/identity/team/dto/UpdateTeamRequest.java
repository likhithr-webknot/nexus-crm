package com.nexus.crm.identity.team.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

/** Every field is optional. {@code removeManager} takes the manager off the team. */
public record UpdateTeamRequest(
	@Size(min = 1, max = 80) String name,
	UUID managerId,
	Boolean removeManager
) {
}
