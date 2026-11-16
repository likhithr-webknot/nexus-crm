package com.nexus.crm.deals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MoveRequest(
	@NotBlank String stageCode,
	Integer version,
	@Size(max = 500) String lostReason
) {
}
