package com.nexus.crm.contacts.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateContactRequest(
	@NotBlank @Size(max = 120) String firstName,
	@NotBlank @Size(max = 120) String lastName,
	@Email @Size(max = 254) String email,
	@Size(max = 40) String phone,
	@Size(max = 120) String jobTitle,
	UUID companyId,
	@Size(max = 160) String companyName,
	UUID ownerId
) {
}
