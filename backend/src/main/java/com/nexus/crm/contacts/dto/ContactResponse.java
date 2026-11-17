package com.nexus.crm.contacts.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nexus.crm.shared.Ref;
import com.nexus.crm.tags.dto.TagRef;

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
	Integer version,
	List<TagRef> tags
) {
}
