package com.nexus.crm.leads.dto;

public record LeadSummaryResponse(
	long total,
	long newThisMonth,
	long qualified,
	long unassigned,
	long active,
	Double totalChange,
	Double newThisMonthChange
) {
}
