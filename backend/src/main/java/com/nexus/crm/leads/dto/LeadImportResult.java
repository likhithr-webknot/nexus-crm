package com.nexus.crm.leads.dto;

import java.util.List;

public record LeadImportResult(
	int total,
	int imported,
	List<LeadImportRowError> errors
) {
}
