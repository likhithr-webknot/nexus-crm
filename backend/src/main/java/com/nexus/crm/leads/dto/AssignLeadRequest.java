package com.nexus.crm.leads.dto;

import java.util.UUID;

/** A null ownerId puts the lead back in the unassigned queue. */
public record AssignLeadRequest(
	UUID ownerId
) {
}
