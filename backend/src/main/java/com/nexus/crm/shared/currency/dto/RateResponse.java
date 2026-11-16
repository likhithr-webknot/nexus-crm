package com.nexus.crm.shared.currency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One unit of {@code currency} is worth {@code rateToBase} units of the base currency. */
public record RateResponse(
	String currency,
	BigDecimal rateToBase,
	LocalDate rateDate,
	String source,
	boolean stale
) {

	public static RateResponse from(Rate rate) {
		return new RateResponse(rate.currency(), rate.rateToBase(), rate.rateDate(), rate.source(), rate.stale());
	}

}
