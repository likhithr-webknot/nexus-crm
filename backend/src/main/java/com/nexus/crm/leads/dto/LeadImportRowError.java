package com.nexus.crm.leads.dto;

public record LeadImportRowError(
	int row,
	String reason
) {
}
