package com.nexus.crm.deals.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.shared.currency.Money;

public record CreateDealRequest(
	@NotBlank @Size(max = 160) String name,
	@NotNull UUID companyId,
	UUID primaryContactId,
	UUID ownerId,
	String stageCode,
	@NotNull @Valid Money amount,
	LocalDate expectedCloseDate
) {
}
