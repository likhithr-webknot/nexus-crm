package com.nexus.crm.shared.currency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A rate that can be used to convert an amount to the base currency. */
public record Rate(
	String currency,
	BigDecimal rateToBase,
	LocalDate rateDate,
	String source,
	boolean stale
) {
}
