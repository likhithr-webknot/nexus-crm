package com.nexus.crm.activities.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.activities.Activity;

public record LogActivityRequest(
	@NotNull Activity.Type type,
	@NotBlank @Size(max = 160) String subject,
	@Size(max = 5000) String body,
	Instant occurredAt,
	UUID leadId,
	UUID contactId,
	UUID dealId
) {
}
