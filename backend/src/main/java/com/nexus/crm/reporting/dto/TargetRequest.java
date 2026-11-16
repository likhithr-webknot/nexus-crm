package com.nexus.crm.reporting.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TargetRequest(
	@NotNull @DecimalMin("0") BigDecimal amount
) {
}
