package com.nexus.crm.followups.dto;

import java.util.UUID;

import com.nexus.crm.followups.FollowUp;
import com.nexus.crm.shared.Label;

public record FollowUpNames(
	java.util.Map<UUID, Label> leads,
	java.util.Map<UUID, Label> contacts,
	java.util.Map<UUID, Label> deals,
	java.util.Map<UUID, String> assignees
) {

	public Label about(FollowUp f) {
		if (f.getContactId() != null) {
			return this.contacts.get(f.getContactId());
		}
		return (f.getLeadId() != null) ? this.leads.get(f.getLeadId()) : this.deals.get(f.getDealId());
	}

}
