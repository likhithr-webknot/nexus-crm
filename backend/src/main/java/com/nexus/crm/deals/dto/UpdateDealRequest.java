package com.nexus.crm.deals.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.nexus.crm.shared.currency.Money;

public record UpdateDealRequest(
	Integer version,
	@Size(max = 160) String name,
	UUID primaryContactId,
	UUID ownerId,
	@Valid Money amount,
	LocalDate expectedCloseDate
) {
}
