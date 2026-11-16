package com.nexus.crm.activities.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.activities.Activity;
import com.nexus.crm.shared.Label;

public record ActivityResponse(
	UUID id,
	Activity.Type type,
	String subject,
	String body,
	Instant occurredAt,
	UUID actorId,
	String actorName,
	UUID leadId,
	UUID contactId,
	UUID dealId,
	Label about,
	Instant createdAt,
	Integer version
) {
}
