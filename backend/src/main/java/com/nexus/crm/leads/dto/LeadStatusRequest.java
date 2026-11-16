package com.nexus.crm.leads.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.leads.Lead;

public record LeadStatusRequest(
	@NotNull Lead.Status status,
	@Size(max = 500) String reason
) {
}
