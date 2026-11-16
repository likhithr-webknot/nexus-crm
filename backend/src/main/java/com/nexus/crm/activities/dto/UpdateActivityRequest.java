package com.nexus.crm.activities.dto;

import jakarta.validation.constraints.Size;

public record UpdateActivityRequest(
	Integer version,
	@Size(max = 160) String subject,
	@Size(max = 5000) String body
) {
}
