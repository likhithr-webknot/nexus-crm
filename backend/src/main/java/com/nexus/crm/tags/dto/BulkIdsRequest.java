package com.nexus.crm.tags.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Several record IDs, for a bulk action that needs nothing else. */
public record BulkIdsRequest(
	@NotEmpty @Size(max = 500) List<UUID> ids
) {
}
