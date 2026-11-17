package com.nexus.crm.deals.dto;

import java.util.UUID;

/** Filters for the deal list. Any of them may be null. */
public record DealFilter(
	String query,
	String stageCode,
	UUID ownerId,
	UUID companyId,
	UUID contactId,
	Boolean open,
	UUID tagId
) {
}
