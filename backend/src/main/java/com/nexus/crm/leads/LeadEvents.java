package com.nexus.crm.leads;

import java.util.UUID;

/** Things that happen to leads. Other modules listen for them, for example to write the timeline entry. */
public final class LeadEvents {

	private LeadEvents() {
	}

	/** A lead was given to someone, or taken off its owner when {@code newOwnerId} is null. */
	public record Assigned(UUID leadId, String name, UUID newOwnerId, UUID actorId) {
	}

	public record StatusChanged(UUID leadId, String name, Lead.Status from, Lead.Status to, UUID actorId) {
	}

	/** A lead became a deal. */
	public record Converted(UUID leadId, String name, UUID dealId, UUID companyId, UUID contactId, UUID actorId) {
	}

}
