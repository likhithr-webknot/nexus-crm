package com.nexus.crm.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Role;

/** Any signed-in user can see users. Only admins can invite or change them. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class UserController {

	record UserResponse(UUID id, String email, String displayName, String avatarUrl, Role role, AppUser.Status status,
			UUID teamId, Instant lastLoginAt) {

		static UserResponse from(AppUser user) {
			return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
					user.getRole(), user.getStatus(), user.getTeamId(), user.getLastLoginAt());
		}

	}

	record InviteUserRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 120) String displayName, @NotNull Role role) {
	}

	/** Both fields are optional. A field that is left out is not changed. */
	record UpdateUserRequest(Role role, AppUser.Status status) {
	}

	private final UserService users;

	/** Returns the signed-in user. The frontend calls this to find out whether someone is signed in. */
	@GetMapping("/me")
	UserResponse me(@AuthenticationPrincipal CurrentUser user) {
		return UserResponse.from(this.users.get(user.id()));
	}

	@GetMapping("/users")
	List<UserResponse> list() {
		return this.users.list().stream().map(UserResponse::from).toList();
	}

	@PostMapping("/users")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse invite(@Valid @RequestBody InviteUserRequest request, @AuthenticationPrincipal CurrentUser admin) {
		return UserResponse.from(this.users.invite(request.email(), request.displayName(), request.role(), admin.id()));
	}

	@PatchMapping("/users/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	UserResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal CurrentUser admin) {
		return UserResponse.from(this.users.update(id, request.role(), request.status(), admin.id()));
	}

}
