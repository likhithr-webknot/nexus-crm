package com.nexus.crm.identity.team;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.AppUserRepository;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Teams: a group of reps led by a manager. Admins create, rename and delete them. A team can only be
 * deleted when nobody is in it, so no user is ever left pointing at a team that is gone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

	/** A team with how many people are in it. */
	public record TeamInfo(Team team, String managerName, long members) {
	}

	private final TeamRepository teams;

	private final AppUserRepository users;

	@Transactional(readOnly = true)
	public List<TeamInfo> list() {
		return this.teams.findAll(Sort.by("name")).stream().map(this::info).toList();
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

	/** Changes the name, the manager, or both. A null argument leaves that field alone; {@code removeManager} clears it. */
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
		String manager = (team.getManagerId() == null) ? null
				: this.users.findById(team.getManagerId()).map(AppUser::getDisplayName).orElse(null);
		return new TeamInfo(team, manager, this.users.countByTeamId(team.getId()));
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

}
