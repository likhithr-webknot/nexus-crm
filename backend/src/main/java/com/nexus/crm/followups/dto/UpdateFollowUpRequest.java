package com.nexus.crm.followups.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Size;

import com.nexus.crm.followups.FollowUp;

public record UpdateFollowUpRequest(
	Integer version,
	@Size(max = 160) String title,
	FollowUp.Type type,
	@Size(max = 2000) String notes,
	Instant dueAt,
	UUID assigneeId
) {
}
