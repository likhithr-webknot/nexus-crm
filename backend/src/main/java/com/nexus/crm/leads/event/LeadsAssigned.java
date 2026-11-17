package com.nexus.crm.leads.event;

import java.util.List;
import java.util.UUID;

/**
 * Several leads were given to someone at once (or taken off their owner when {@code newOwnerId} is null). Sent once for
 * the whole batch, so the new owner gets one notification instead of one per lead.
 */
public record LeadsAssigned(
	List<UUID> leadIds,
	UUID newOwnerId,
	UUID actorId
) {
}
