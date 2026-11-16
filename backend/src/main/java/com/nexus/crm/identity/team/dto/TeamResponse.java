package com.nexus.crm.identity.team.dto;

import java.util.UUID;

import com.nexus.crm.identity.team.Team;

public record TeamResponse(
	UUID id,
	String name,
	UUID managerId,
	String managerName,
	long memberCount
) {

	public static TeamResponse from(TeamInfo info) {
		Team team = info.team();
		return new TeamResponse(team.getId(), team.getName(), team.getManagerId(), info.managerName(), info.members());
	}

}
