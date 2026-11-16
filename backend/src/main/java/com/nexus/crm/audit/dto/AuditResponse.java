package com.nexus.crm.audit.dto;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

import com.nexus.crm.audit.AuditLog;

public record AuditResponse(
	long id,
	Instant occurredAt,
	UUID actorId,
	String actorName,
	String entityType,
	UUID entityId,
	AuditLog.Action action,
	JsonNode changes
) {
}
