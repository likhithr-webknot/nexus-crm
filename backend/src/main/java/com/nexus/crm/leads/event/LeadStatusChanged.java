package com.nexus.crm.leads.event;

import java.util.UUID;

import com.nexus.crm.leads.Lead;

public record LeadStatusChanged(
	UUID leadId,
	String name,
	Lead.Status from,
	Lead.Status to,
	UUID actorId
) {
}
