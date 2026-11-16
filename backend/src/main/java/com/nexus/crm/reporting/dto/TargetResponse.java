package com.nexus.crm.reporting.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

public record TargetResponse(
	String month,
	@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount
) {
}
