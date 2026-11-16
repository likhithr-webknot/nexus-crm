package com.nexus.crm.deals.event;

import java.util.UUID;

/** A deal moved to another stage, including won and lost. */
public record DealStageChanged(
	UUID dealId,
	String name,
	String fromStage,
	String toStage,
	String toStageName,
	UUID ownerId,
	UUID actorId,
	String lostReason,
	UUID contactId
) {
}
