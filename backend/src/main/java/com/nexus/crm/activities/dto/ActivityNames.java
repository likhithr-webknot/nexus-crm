package com.nexus.crm.activities.dto;

import java.util.Map;
import java.util.UUID;

import com.nexus.crm.shared.Label;

public record ActivityNames(
	Map<UUID, Label> leads,
	Map<UUID, Label> contacts,
	Map<UUID, Label> deals,
	Map<UUID, String> actors
) {

	/** The most specific thing the entry is about: the contact, else the lead, else the deal. */
	public Label subjectOf(UUID leadId, UUID contactId, UUID dealId) {
		if (contactId != null) {
			return this.contacts.get(contactId);
		}
		return (leadId != null) ? this.leads.get(leadId) : this.deals.get(dealId);
	}

}
