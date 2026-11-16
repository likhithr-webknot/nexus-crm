package com.nexus.crm.followups.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.followups.FollowUp;

/** Any may be null. {@code from} is included and {@code to} is not. */
public record FollowUpFilter(
	Instant from,
	Instant to,
	FollowUp.Status status,
	UUID assigneeId,
	UUID leadId,
	UUID contactId,
	UUID dealId
) {
}
