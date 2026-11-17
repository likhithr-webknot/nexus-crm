package com.nexus.crm.leads.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Gives several leads to one person, or to nobody when {@code ownerId} is null. */
public record BulkAssignRequest(
	@NotEmpty @Size(max = 500) List<UUID> ids,
	UUID ownerId
) {
}
