package com.nexus.crm.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.error.ApiException;

/** Two admins changing each other at the same moment must not leave the app with no admin. */
class LastAdminTests {

	private final AppUserRepository repository = mock(AppUserRepository.class);

	private final UserService service = new UserService(this.repository, mock(ApplicationEventPublisher.class));

	private static AppUser activeAdmin() {
		AppUser admin = AppUser.invite("admin-" + UUID.randomUUID() + "@acme.example", "Admin", Role.ADMIN, null);
		admin.signedIn(null, Instant.now());
		return admin;
	}

	private AppUser target(List<AppUser> activeAdminsNow) {
		AppUser target = activeAdmin();
		when(this.repository.findById(target.getId())).thenReturn(Optional.of(target));
		when(this.repository.lockByRoleAndStatus(any(), any())).thenReturn(activeAdminsNow);
		return target;
	}

	@Test
	void theOnlyActiveAdminCannotBeDemoted() {
		AppUser target = activeAdmin();
		when(this.repository.findById(target.getId())).thenReturn(Optional.of(target));
		when(this.repository.lockByRoleAndStatus(any(), any())).thenReturn(List.of(target));

		assertThatThrownBy(() -> this.service.update(target.getId(), Role.REP, null, UUID.randomUUID()))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("at least one active admin");
		assertThat(target.getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void theOnlyActiveAdminCannotBeDisabled() {
		AppUser target = activeAdmin();
		when(this.repository.findById(target.getId())).thenReturn(Optional.of(target));
		when(this.repository.lockByRoleAndStatus(any(), any())).thenReturn(List.of(target));

		assertThatThrownBy(() -> this.service.update(target.getId(), null, AppUser.Status.DISABLED, UUID.randomUUID()))
			.isInstanceOf(ApiException.class);
		assertThat(target.getStatus()).isEqualTo(AppUser.Status.ACTIVE);
	}

	@Test
	void anAdminCanGoWhenAnotherActiveAdminRemains() {
		AppUser other = activeAdmin();
		AppUser target = activeAdmin();
		when(this.repository.findById(target.getId())).thenReturn(Optional.of(target));
		when(this.repository.lockByRoleAndStatus(any(), any())).thenReturn(List.of(target, other));

		this.service.update(target.getId(), Role.REP, null, UUID.randomUUID());

		assertThat(target.getRole()).isEqualTo(Role.REP);
	}

	@Test
	void changingANonAdminNeverNeedsTheCheck() {
		AppUser rep = AppUser.invite("rep-" + UUID.randomUUID() + "@acme.example", "Rep", Role.REP, null);
		rep.signedIn(null, Instant.now());
		when(this.repository.findById(rep.getId())).thenReturn(Optional.of(rep));

		this.service.update(rep.getId(), Role.MANAGER, null, UUID.randomUUID());

		assertThat(rep.getRole()).isEqualTo(Role.MANAGER);
	}

}
