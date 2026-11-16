package com.nexus.crm.identity.team.dto;

import com.nexus.crm.identity.team.Team;

/** A team with how many people are in it. */
public record TeamInfo(
	Team team,
	String managerName,
	long members
) {
}
