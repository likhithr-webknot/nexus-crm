package com.nexus.crm.shared.currency.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCurrencyRequest(
	@NotNull Boolean enabled
) {
}
