package com.nexus.crm.modules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ModuleInput(String name, String status, String account, String description,
		BigDecimal amount, List<Map<String, Object>> lineItems, List<Map<String, Object>> flowNodes,
		List<Map<String, Object>> flowEdges, String recipient, List<String> cc, List<String> bcc, String bodyHtml,
		Integer version) {
}
