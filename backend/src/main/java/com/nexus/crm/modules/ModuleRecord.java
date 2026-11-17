package com.nexus.crm.modules;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ModuleRecord(UUID id, String name, String status, String owner, String account,
		String description, BigDecimal amount, Instant createdAt, Instant updatedAt, int version,
		List<Map<String, Object>> lineItems, List<Map<String, Object>> flowNodes,
		List<Map<String, Object>> flowEdges, String recipient, List<String> cc, List<String> bcc, String bodyHtml) {
}
