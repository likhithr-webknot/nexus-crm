package com.nexus.crm.leads.dto;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.nexus.crm.leads.Lead;
import com.nexus.crm.shared.currency.Money;

public record CreateLeadRequest(
	@NotBlank @Size(max = 120) String firstName,
	@Size(max = 120) String lastName,
	@Email @Size(max = 254) String email,
	@Size(max = 40) String phone,
	@Size(max = 160) String companyName,
	@Size(max = 120) String jobTitle,
	Lead.Source source,
	UUID ownerId,
	Boolean unassigned,
	@Valid Money estimatedValue
) {
}
