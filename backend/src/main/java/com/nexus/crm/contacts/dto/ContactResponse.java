package com.nexus.crm.contacts.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.shared.Ref;

public record ContactResponse(
	UUID id,
	String firstName,
	String lastName,
	String name,
	String jobTitle,
	String email,
	String phone,
	Ref company,
	Ref owner,
	Instant createdAt,
	Instant updatedAt,
	Integer version
) {
}
