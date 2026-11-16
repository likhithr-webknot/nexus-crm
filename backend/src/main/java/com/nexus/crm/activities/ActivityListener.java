package com.nexus.crm.activities;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexus.crm.deals.event.DealCreated;
import com.nexus.crm.deals.event.DealReassigned;
import com.nexus.crm.deals.event.DealStageChanged;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.leads.event.LeadAssigned;
import com.nexus.crm.leads.event.LeadConverted;
import com.nexus.crm.leads.event.LeadStatusChanged;

/** Writes the automatic timeline entries. Runs inside the transaction that caused the event. */
@Component
@RequiredArgsConstructor
class ActivityListener {

	private final ActivityService activities;

	private final UserService users;

	@EventListener
	void onLeadAssigned(LeadAssigned e) {
		String text = (e.newOwnerId() == null) ? "Moved back to the unassigned queue."
				: "Assigned to " + this.users.displayNames(java.util.Set.of(e.newOwnerId())).get(e.newOwnerId()) + ".";
		this.activities.system("Lead assigned", text, e.actorId(), e.leadId(), null, null);
	}

	@EventListener
	void onLeadStatusChanged(LeadStatusChanged e) {
		this.activities.system("Status changed to " + label(e.to().name()), "Moved from " + label(e.from().name()) + ".",
				e.actorId(), e.leadId(), null, null);
	}

	@EventListener
	void onLeadConverted(LeadConverted e) {
		this.activities.system("Lead converted", "Became the deal " + e.name() + ".", e.actorId(), e.leadId(),
				e.contactId(), e.dealId());
	}

	@EventListener
	void onDealCreated(DealCreated e) {
		// A deal made by converting a lead is already covered by the "Lead converted" entry.
		if (e.leadId() == null) {
			this.activities.system("Deal created", e.name(), e.actorId(), null, e.contactId(), e.dealId());
		}
	}

	@EventListener
	void onDealStageChanged(DealStageChanged e) {
		String detail = (e.lostReason() != null) ? "Reason: " + e.lostReason() : "Moved from " + label(e.fromStage()) + ".";
		this.activities.system("Stage changed to " + e.toStageName(), detail, e.actorId(), null, e.contactId(), e.dealId());
	}

	@EventListener
	void onDealReassigned(DealReassigned e) {
		this.activities.system("Deal reassigned",
				"Now owned by " + this.users.displayNames(java.util.Set.of(e.newOwnerId())).get(e.newOwnerId()) + ".",
				e.actorId(), null, e.contactId(), e.dealId());
	}

	/** "NEGOTIATION" becomes "Negotiation". */
	private static String label(String code) {
		String lower = code.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

}
