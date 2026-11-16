package com.nexus.crm.followups.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.followups.FollowUp;
import com.nexus.crm.shared.Label;

public record FollowUpResponse(
	UUID id,
	String title,
	FollowUp.Type type,
	String notes,
	Instant dueAt,
	FollowUp.Status status,
	Instant completedAt,
	UUID assigneeId,
	String assigneeName,
	UUID leadId,
	UUID contactId,
	UUID dealId,
	Label about,
	Instant createdAt,
	Integer version
) {
}
