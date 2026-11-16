package com.nexus.crm.followups.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.followups.FollowUp;

public record CreateFollowUpRequest(
	@NotBlank @Size(max = 160) String title,
	FollowUp.Type type,
	@Size(max = 2000) String notes,
	@NotNull Instant dueAt,
	UUID assigneeId,
	UUID leadId,
	UUID contactId,
	UUID dealId
) {
}
