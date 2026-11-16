package com.nexus.crm.followups.dto;

/** The tiles on the follow-ups page. */
public record FollowUpSummary(
	long today,
	long todayRemaining,
	long upcoming,
	long overdue,
	long completedThisMonth
) {
}
