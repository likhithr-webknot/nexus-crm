package com.nexus.crm.leads.dto;

/** Counts for the tiles on the leads page. A change is null when it cannot be worked out. */
public record LeadSummary(
	long total,
	long newThisMonth,
	long qualified,
	long unassigned,
	long active,
	Double totalChange,
	Double newThisMonthChange
) {
}
