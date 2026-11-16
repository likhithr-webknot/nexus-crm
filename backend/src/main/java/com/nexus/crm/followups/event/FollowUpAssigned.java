package com.nexus.crm.followups.event;

import java.util.UUID;

/** A follow-up was given to someone other than the person who made it. */
public record FollowUpAssigned(
	UUID followUpId,
	String title,
	UUID assigneeId,
	UUID actorId
) {
}
