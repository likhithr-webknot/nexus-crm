package com.nexus.crm.shared.currency.dto;

import com.nexus.crm.shared.currency.Currency;

public record CurrencyResponse(
	String code,
	String name,
	int minorUnits,
	boolean enabled,
	boolean base
) {

	public static CurrencyResponse from(Currency currency) {
		return new CurrencyResponse(currency.getCode(), currency.getName(), currency.getMinorUnits(),
				currency.isEnabled(), currency.isBase());
	}

}
