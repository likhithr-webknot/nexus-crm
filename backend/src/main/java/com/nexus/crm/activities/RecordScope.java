package com.nexus.crm.activities;

import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import com.nexus.crm.contacts.Contact;
import com.nexus.crm.deals.Deal;
import com.nexus.crm.leads.Lead;

/**
 * Activities and follow-ups belong to a lead, a contact or a deal. A user may see one when they can see
 * what it belongs to. This builds that check as a query condition, so every list applies it.
 */
public final class RecordScope {

	private RecordScope() {
	}

	/**
	 * A condition that is true for rows linked to a lead, contact or deal owned by one of {@code ownerIds}
	 * (or an unassigned lead). Pass null for an admin, who sees everything.
	 */
	public static <T> Predicate linkedTo(Set<UUID> ownerIds, Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
		if (ownerIds == null) {
			return cb.conjunction();
		}
		var leads = query.subquery(UUID.class);
		var lead = leads.from(Lead.class);
		leads.select(lead.get("id")).where(cb.or(lead.get("ownerId").in(ownerIds), cb.isNull(lead.get("ownerId"))));

		var contacts = query.subquery(UUID.class);
		var contact = contacts.from(Contact.class);
		contacts.select(contact.get("id")).where(contact.get("ownerId").in(ownerIds));

		var deals = query.subquery(UUID.class);
		var deal = deals.from(Deal.class);
		deals.select(deal.get("id")).where(deal.get("ownerId").in(ownerIds));

		Path<UUID> leadId = root.get("leadId");
		Path<UUID> contactId = root.get("contactId");
		Path<UUID> dealId = root.get("dealId");
		return cb.or(leadId.in(leads), contactId.in(contacts), dealId.in(deals));
	}

}
