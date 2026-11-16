package com.nexus.crm.identity.team.dto;

import java.util.UUID;

/** A null teamId takes the user out of their team. */
public record SetTeamRequest(
	UUID teamId
) {
}
