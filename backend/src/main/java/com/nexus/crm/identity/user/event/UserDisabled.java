package com.nexus.crm.identity.user.event;

import java.util.UUID;

/** A user was disabled. The auth module ends their sessions when it hears this. */
public record UserDisabled(
	UUID userId
) {
}
