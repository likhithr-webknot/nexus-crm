package com.nexus.crm.reporting.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

public record MonthResponse(
	String month,
	double revenue,
	@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal target
) {
}
