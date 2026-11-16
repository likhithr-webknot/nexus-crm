package com.nexus.crm.shared.currency;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** An amount with its currency, as the API sends and receives it. */
public record Money(@NotNull @DecimalMin("0") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
		@NotBlank String currency) {

	/** Null in, null out, so an optional amount can be passed straight through. */
	public static Money of(BigDecimal amount, String currency) {
		return (amount == null) ? null : new Money(amount, currency);
	}

}
