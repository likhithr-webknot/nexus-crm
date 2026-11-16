package com.nexus.crm.leads.event;

import java.util.UUID;

/** A lead was given to someone, or taken off its owner when {@code newOwnerId} is null. */
public record LeadAssigned(
	UUID leadId,
	String name,
	UUID newOwnerId,
	UUID actorId
) {
}
