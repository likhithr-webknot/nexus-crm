package com.nexus.crm.leads.dto;

import java.util.UUID;

import com.nexus.crm.leads.Lead;
import com.nexus.crm.shared.currency.Money;

/** The fields of a lead. On update, a null field is left as it is. */
public record LeadInput(
	String firstName,
	String lastName,
	String email,
	String phone,
	UUID companyId,
	String companyName,
	String jobTitle,
	Lead.Source source,
	UUID ownerId,
	boolean unassigned,
	Money estimatedValue
) {
}
