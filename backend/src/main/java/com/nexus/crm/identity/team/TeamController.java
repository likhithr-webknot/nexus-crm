package com.nexus.crm.identity.team;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
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

import com.nexus.crm.identity.team.dto.CreateTeamRequest;
import com.nexus.crm.identity.team.dto.TeamResponse;
import com.nexus.crm.identity.team.dto.UpdateTeamRequest;

/** Anyone signed in can see teams. Only admins can create, change or delete them. */
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
class TeamController {

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
