package com.nexus.crm.contacts.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.shared.Ref;

public record CompanyResponse(
	UUID id,
	String name,
	String domain,
	String industry,
	Ref owner,
	Instant createdAt,
	Instant updatedAt,
	Integer version
) {
}
