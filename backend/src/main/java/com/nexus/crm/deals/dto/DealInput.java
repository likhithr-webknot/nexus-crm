package com.nexus.crm.deals.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.nexus.crm.shared.currency.Money;

/** The fields of a deal. On update, a null field is left as it is. */
public record DealInput(
	String name,
	UUID companyId,
	UUID primaryContactId,
	UUID ownerId,
	String stageCode,
	Money amount,
	LocalDate expectedCloseDate,
	UUID leadId,
	String source
) {
}
