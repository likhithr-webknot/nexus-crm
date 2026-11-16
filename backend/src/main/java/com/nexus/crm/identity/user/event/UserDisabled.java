package com.nexus.crm.identity.user.event;

import java.util.UUID;

/**
 * A user was disabled. The auth module ends their sessions when it hears this.
 *
 * <p>
 * Problem we had (circular dependency): {@code UserService} called {@code SessionService} to end sessions, while
 * the auth classes already used {@code UserService}. Two packages depending on each other cannot be understood or
 * changed alone. How we fixed it, in order: (1) drew the package graph from the imports and found the cycles
 * between identity.user, identity.auth and identity.team; (2) made {@code user} the base that depends on nothing
 * else in identity; (3) replaced the direct call with this event, which auth listens to; (4) moved the first-admin
 * bootstrap to {@code BootstrapAdmin} in auth; (5) moved team membership to {@code TeamService};
 * (6) added {@code ModuleDependencyTests}, which fails if a cycle comes back.
 */
public record UserDisabled(
	UUID userId
) {
}
