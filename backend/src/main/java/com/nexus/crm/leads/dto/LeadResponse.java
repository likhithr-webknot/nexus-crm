package com.nexus.crm.leads.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.leads.Lead;
import com.nexus.crm.shared.Ref;
import com.nexus.crm.shared.currency.Money;

public record LeadResponse(
	UUID id,
	String firstName,
	String lastName,
	String name,
	String email,
	String phone,
	String companyName,
	String jobTitle,
	Lead.Source source,
	Lead.Status status,
	Ref owner,
	Money estimatedValue,
	String disqualifyReason,
	UUID convertedDealId,
	Instant createdAt,
	Instant updatedAt,
	Integer version
) {
}
