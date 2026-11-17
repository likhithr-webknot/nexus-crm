package com.nexus.crm.tags.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Adds and removes tags on several records at once. */
public record BulkTagRequest(
	@NotEmpty @Size(max = 500) List<UUID> ids,
	@Size(max = 20) List<UUID> add,
	@Size(max = 20) List<UUID> remove
) {
}
