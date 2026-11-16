package com.nexus.crm.leads.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.nexus.crm.shared.currency.Money;

public record ConvertLeadRequest(
	@Size(max = 160) String dealName,
	@Valid Money amount,
	LocalDate expectedCloseDate
) {
}
