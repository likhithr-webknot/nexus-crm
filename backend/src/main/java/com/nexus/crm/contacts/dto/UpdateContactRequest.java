package com.nexus.crm.contacts.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Every field is optional. A field that is left out is not changed. */
public record UpdateContactRequest(
	Integer version,
	@Size(max = 120) String firstName,
	@Size(max = 120) String lastName,
	@Email @Size(max = 254) String email,
	@Size(max = 40) String phone,
	@Size(max = 120) String jobTitle,
	UUID companyId,
	@Size(max = 160) String companyName,
	UUID ownerId
) {
}
