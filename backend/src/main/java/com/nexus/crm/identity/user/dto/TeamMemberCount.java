package com.nexus.crm.identity.user.dto;

import java.util.UUID;

/** How many users are in a team. Read in one query for all teams. */
public record TeamMemberCount(
	UUID teamId,
	long members
) {
}
