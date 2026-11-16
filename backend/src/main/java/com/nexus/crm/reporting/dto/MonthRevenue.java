package com.nexus.crm.reporting.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthRevenue(
	YearMonth month,
	double revenue,
	BigDecimal target
) {
}
