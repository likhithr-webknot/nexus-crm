package com.nexus.crm.deals.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.nexus.crm.shared.Ref;
import com.nexus.crm.shared.currency.Money;

/** A deal. {@code baseAmount} is the INR value saved when the deal was won or lost, with the rate used. */
public record DealResponse(
	UUID id,
	String name,
	Ref company,
	Ref primaryContact,
	Ref owner,
	String stageCode,
	Money amount,
	@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal baseAmount,
	@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal fxRate,
	LocalDate fxRateDate,
	LocalDate expectedCloseDate,
	Instant closedAt,
	String lostReason,
	UUID leadId,
	Instant createdAt,
	Instant updatedAt,
	Integer version
) {
}
