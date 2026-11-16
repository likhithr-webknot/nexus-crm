package com.nexus.crm.leads.event;

import java.util.UUID;

/** A lead became a deal. */
public record LeadConverted(
	UUID leadId,
	String name,
	UUID dealId,
	UUID companyId,
	UUID contactId,
	UUID actorId
) {
}
