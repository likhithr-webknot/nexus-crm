package com.nexus.crm.activities.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.activities.Activity;

public record ActivityInput(
	Activity.Type type,
	String subject,
	String body,
	Instant occurredAt,
	UUID leadId,
	UUID contactId,
	UUID dealId
) {
}
