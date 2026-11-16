package com.nexus.crm.identity.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexus.crm.identity.user.UserService;

/** Invites the configured first admin when the app starts and there are no users yet. */
@Component
@RequiredArgsConstructor
class BootstrapAdmin {

	private final UserService users;

	private final AuthProperties properties;

	@EventListener(ApplicationReadyEvent.class)
	void inviteFirstAdmin() {
		this.users.inviteFirstAdmin(this.properties.bootstrapAdminEmail());
	}

}
