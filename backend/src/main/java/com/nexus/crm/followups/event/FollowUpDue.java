package com.nexus.crm.followups.event;

import java.util.UUID;

/** A follow-up reached its due time. */
public record FollowUpDue(
	UUID followUpId,
	String title,
	UUID assigneeId
) {
}
