package com.nexus.crm.identity.team;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.identity.team.dto.TeamInfo;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.AppUserRepository;
import com.nexus.crm.identity.user.dto.TeamMemberCount;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** Teams: a group of reps led by a manager. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

	private final TeamRepository teams;

	private final AppUserRepository users;

	@Transactional(readOnly = true)
	public List<TeamInfo> list() {
		return infos(this.teams.findAll(Sort.by("name")));
	}

	@Transactional
	public TeamInfo create(String name, UUID managerId) {
		String clean = name.strip();
		if (this.teams.existsByName(clean)) {
			throw new ApiException(ErrorCode.DUPLICATE, "A team with this name already exists.");
		}
		checkManager(managerId);
		TeamInfo created = info(this.teams.save(new Team(clean, managerId)));
		log.info("Team created: teamId={}", created.team().getId());
		return created;
	}

	/** Changes the name, the manager, or both. */
	@Transactional
	public TeamInfo update(UUID id, String name, UUID managerId, boolean removeManager) {
		Team team = find(id);
		if (name != null && !name.strip().equals(team.getName())) {
			if (this.teams.existsByNameAndIdNot(name.strip(), id)) {
				throw new ApiException(ErrorCode.DUPLICATE, "A team with this name already exists.");
			}
			team.setName(name.strip());
		}
		if (removeManager) {
			team.setManagerId(null);
		}
		else if (managerId != null) {
			checkManager(managerId);
			team.setManagerId(managerId);
		}
		return info(team);
	}

	@Transactional
	public void delete(UUID id) {
		Team team = find(id);
		long members = this.users.countByTeamId(id);
		if (members > 0) {
			throw new ApiException(ErrorCode.INVALID_STATE,
					"This team still has " + members + (members == 1 ? " member" : " members") + ". Move them to another team first.");
		}
		this.teams.delete(team);
		log.info("Team deleted: teamId={}", id);
	}

	private Team find(UUID id) {
		return this.teams.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Team not found."));
	}

	private TeamInfo info(Team team) {
		return infos(List.of(team)).getFirst();
	}

	/** Adds each team's manager name and member count. Two queries however many teams there are. */
	private List<TeamInfo> infos(List<Team> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Set<UUID> managerIds = list.stream().map(Team::getManagerId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<UUID, String> managerNames = managerIds.isEmpty() ? Map.of()
				: this.users.findByIdIn(managerIds).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
		Map<UUID, Long> members = this.users.countMembersByTeam(list.stream().map(Team::getId).toList())
			.stream()
			.collect(Collectors.toMap(TeamMemberCount::teamId, TeamMemberCount::members));
		return list.stream()
			.map((team) -> new TeamInfo(team, (team.getManagerId() == null) ? null : managerNames.get(team.getManagerId()), members.getOrDefault(team.getId(), 0L)))
			.toList();
	}

	private void checkManager(UUID managerId) {
		if (managerId == null) {
			return;
		}
		AppUser manager = this.users.findById(managerId)
			.orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "That manager does not exist."));
		if (manager.getRole() == Role.REP) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "A team can only be led by a manager or an admin.");
		}
	}

	/** Puts a user in a team, or takes them out of theirs when {@code teamId} is null. */
	@Transactional
	public AppUser setMember(UUID userId, UUID teamId) {
		AppUser user = this.users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
		if (teamId != null && !this.teams.existsById(teamId)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Team not found.");
		}
		user.setTeamId(teamId);
		return user;
	}

	/** Active admins, plus the manager of this user's team. They are told about big events such as a won deal. */
	@Transactional(readOnly = true)
	public Set<UUID> leadersOf(UUID userId) {
		Set<UUID> leaders = this.users.findByRoleAndStatus(Role.ADMIN, AppUser.Status.ACTIVE)
			.stream()
			.map(AppUser::getId)
			.collect(Collectors.toCollection(HashSet::new));
		this.users.findById(userId)
			.map(AppUser::getTeamId)
			.flatMap(this.teams::findById)
			.map(Team::getManagerId)
			.ifPresent(leaders::add);
		return leaders;
	}

}
