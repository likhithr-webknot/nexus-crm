package com.nexus.crm.activities.dto;

import java.util.UUID;

import com.nexus.crm.activities.Activity;

public record ActivityFilter(
	Activity.Type type,
	UUID actorId,
	UUID leadId,
	UUID contactId,
	UUID dealId
) {
}
