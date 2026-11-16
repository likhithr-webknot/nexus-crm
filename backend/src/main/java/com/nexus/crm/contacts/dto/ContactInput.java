package com.nexus.crm.contacts.dto;

import java.util.UUID;

/** The fields of a contact. On update, a null field is left as it is. */
public record ContactInput(
	String firstName,
	String lastName,
	String email,
	String phone,
	String jobTitle,
	UUID companyId,
	String companyName,
	UUID ownerId
) {
}
