package com.nexus.crm.leads.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.leads.Lead;

/** Moves several leads to one status. A reason is needed when disqualifying. */
public record BulkStatusRequest(
	@NotEmpty @Size(max = 500) List<UUID> ids,
	@NotNull Lead.Status status,
	@Size(max = 500) String reason
) {
}
