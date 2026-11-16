package com.nexus.crm.contacts.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
	Integer version,
	@Size(max = 160) String name,
	@Size(max = 160) String domain,
	@Size(max = 120) String industry,
	UUID ownerId
) {
}
