package com.nexus.crm.deals;

import java.util.UUID;

/** Things that happen to deals. */
public final class DealEvents {

	private DealEvents() {
	}

	/** A deal was created. {@code leadId} is set when it came from converting a lead. */
	public record Created(UUID dealId, String name, UUID ownerId, UUID actorId, UUID leadId, UUID contactId) {
	}

	/** A deal moved to another stage, including won and lost. */
	public record StageChanged(UUID dealId, String name, String fromStage, String toStage, String toStageName,
			UUID ownerId, UUID actorId, String lostReason, UUID contactId) {
	}

	/** A deal was given to another owner. */
	public record Reassigned(UUID dealId, String name, UUID newOwnerId, UUID actorId, UUID contactId) {
	}

}
