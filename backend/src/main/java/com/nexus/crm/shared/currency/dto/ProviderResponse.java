package com.nexus.crm.shared.currency.dto;

import java.math.BigDecimal;
import java.util.Map;

/** What the exchange-rate provider sends back: how many units of each currency one unit of the base is worth. */
public record ProviderResponse(
	String result,
	Map<String, BigDecimal> rates
) {
}
