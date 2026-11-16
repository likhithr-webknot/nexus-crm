package com.nexus.crm.deals;

import java.util.UUID;

/**
 * Things that happen to deals. Other modules listen for them, for example the
 * activities module writes the timeline entry. They are sent inside the
 * deal's transaction, so a listener's work is saved or undone with the deal.
 */
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
