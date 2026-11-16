package com.nexus.crm.deals.event;

import java.util.UUID;

/** A deal was created. {@code leadId} is set when it came from converting a lead. */
public record DealCreated(
	UUID dealId,
	String name,
	UUID ownerId,
	UUID actorId,
	UUID leadId,
	UUID contactId
) {
}
