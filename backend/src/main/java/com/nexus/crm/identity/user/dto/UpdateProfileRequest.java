package com.nexus.crm.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What a person may change about themselves. A blank job title clears it. */
public record UpdateProfileRequest(
	@NotBlank @Size(max = 120) String displayName,
	@Size(max = 120) String jobTitle
) {
}
