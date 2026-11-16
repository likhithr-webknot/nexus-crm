package com.nexus.crm.identity.team;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Anyone signed in can see teams. Only admins can create, change or delete them. */
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
class TeamController {

	record TeamResponse(UUID id, String name, UUID managerId, String managerName, long memberCount) {

		static TeamResponse from(TeamService.TeamInfo info) {
			Team team = info.team();
			return new TeamResponse(team.getId(), team.getName(), team.getManagerId(), info.managerName(), info.members());
		}

	}

	record CreateTeamRequest(@NotBlank @Size(max = 80) String name, UUID managerId) {
	}

	/** Every field is optional. {@code removeManager} takes the manager off the team. */
	record UpdateTeamRequest(@Size(min = 1, max = 80) String name, UUID managerId, Boolean removeManager) {
	}

	private final TeamService teams;

	@GetMapping
	List<TeamResponse> list() {
		return this.teams.list().stream().map(TeamResponse::from).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	TeamResponse create(@Valid @RequestBody CreateTeamRequest request) {
		return TeamResponse.from(this.teams.create(request.name(), request.managerId()));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	TeamResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTeamRequest request) {
		return TeamResponse.from(
				this.teams.update(id, request.name(), request.managerId(), Boolean.TRUE.equals(request.removeManager())));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.teams.delete(id);
	}

}
