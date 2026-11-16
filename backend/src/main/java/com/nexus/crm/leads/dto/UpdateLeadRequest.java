package com.nexus.crm.leads.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import com.nexus.crm.leads.Lead;
import com.nexus.crm.shared.currency.Money;

/** Every field is optional. A field that is left out is not changed. */
public record UpdateLeadRequest(
	Integer version,
	@Size(max = 120) String firstName,
	@Size(max = 120) String lastName,
	@Email @Size(max = 254) String email,
	@Size(max = 40) String phone,
	@Size(max = 160) String companyName,
	@Size(max = 120) String jobTitle,
	Lead.Source source,
	@Valid Money estimatedValue
) {
}
