package com.nexus.crm.shared;

import java.util.UUID;

/** The user who is signed in on the current request. */
public record CurrentUser(UUID id, Role role, UUID sessionId) {
}
