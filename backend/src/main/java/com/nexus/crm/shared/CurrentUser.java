package com.nexus.crm.shared;

import java.util.UUID;

/**
 * The user who is signed in on the current request. To use it in a controller,
 * add the parameter {@code @AuthenticationPrincipal CurrentUser user}.
 *
 * @param id the user's ID
 * @param role the user's role right now
 * @param sessionId which sign-in (which browser or device) this request comes from
 */
public record CurrentUser(UUID id, Role role, UUID sessionId) {
}
