package com.nexus.crm.leads.dto;

import java.util.UUID;

/** What a conversion created. */
public record LeadConversion(
	UUID leadId,
	UUID companyId,
	UUID contactId,
	UUID dealId
) {
}
