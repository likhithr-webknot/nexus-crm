package com.nexus.crm.contacts.dto;

import java.util.UUID;

public record CompanyInput(
	String name,
	String domain,
	String industry,
	UUID ownerId
) {
}
