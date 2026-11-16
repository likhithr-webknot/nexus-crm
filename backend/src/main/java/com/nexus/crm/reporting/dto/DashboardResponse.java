package com.nexus.crm.reporting.dto;

import java.time.LocalDate;

public record DashboardResponse(
	LocalDate from,
	LocalDate to,
	Dashboard data
) {
}
