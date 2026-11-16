package com.nexus.crm.deals.event;

import java.util.UUID;

/** A deal was given to another owner. */
public record DealReassigned(
	UUID dealId,
	String name,
	UUID newOwnerId,
	UUID actorId,
	UUID contactId
) {
}
