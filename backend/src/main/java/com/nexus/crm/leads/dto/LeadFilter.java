package com.nexus.crm.leads.dto;

import java.util.UUID;

import com.nexus.crm.leads.Lead;

/** The list filters. Any may be null. {@code view} is one of the quick views on the leads page, {@code tagId} limits the list to one tag. */
public record LeadFilter(
	String query,
	Lead.Source source,
	Lead.Status status,
	UUID ownerId,
	boolean unassigned,
	LeadView view,
	UUID tagId
) {
}
