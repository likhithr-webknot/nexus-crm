package com.nexus.crm.leads.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record DeleteLeadsRequest(
	@NotEmpty @Size(max = 500) List<UUID> ids
) {
}
