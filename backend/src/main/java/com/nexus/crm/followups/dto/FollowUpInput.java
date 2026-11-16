package com.nexus.crm.followups.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.followups.FollowUp;

public record FollowUpInput(
	String title,
	FollowUp.Type type,
	String notes,
	Instant dueAt,
	UUID assigneeId,
	UUID leadId,
	UUID contactId,
	UUID dealId
) {
}
