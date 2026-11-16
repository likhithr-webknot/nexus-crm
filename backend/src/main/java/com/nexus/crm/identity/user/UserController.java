package com.nexus.crm.identity.user;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.identity.user.dto.InviteUserRequest;
import com.nexus.crm.identity.user.dto.UpdateProfileRequest;
import com.nexus.crm.identity.user.dto.UpdateUserRequest;
import com.nexus.crm.identity.user.dto.UserResponse;
import com.nexus.crm.shared.CurrentUser;

/** Any signed-in user can see users. Only admins can invite or change them. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class UserController {

	private final UserService users;

	/** Returns the signed-in user. The frontend calls this to find out whether someone is signed in. */
	@GetMapping("/me")
	UserResponse me(@AuthenticationPrincipal CurrentUser user) {
		return UserResponse.from(this.users.get(user.id()));
	}

	/** The signed-in user changes their own name and job title. */
	@PatchMapping("/me")
	UserResponse editProfile(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal CurrentUser user) {
		return UserResponse.from(this.users.editProfile(user.id(), request.displayName(), request.jobTitle()));
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
	UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal CurrentUser admin) {
		if (request.displayName() != null && !request.displayName().isBlank()) {
			this.users.rename(id, request.displayName());
		}
		return UserResponse.from(this.users.update(id, request.role(), request.status(), admin.id()));
	}

	/** The people the signed-in user can give a lead, deal or follow-up to. */
	/** Removes a user who was invited but never signed in. Everyone else is disabled instead. */
	@DeleteMapping("/users/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser admin) {
		this.users.delete(id, admin.id());
	}

	@GetMapping("/users/assignable")
	List<UserResponse> assignable(@AuthenticationPrincipal CurrentUser user) {
		return this.users.assignable(user).stream().map(UserResponse::from).toList();
	}

}
