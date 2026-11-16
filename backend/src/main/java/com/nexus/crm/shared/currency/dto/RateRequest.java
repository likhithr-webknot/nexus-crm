package com.nexus.crm.shared.currency.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RateRequest(
	@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal rateToBase
) {
}
