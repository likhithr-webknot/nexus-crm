package com.nexus.crm.leads;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

	/** For the duplicate warning. Pass the email through {@code Emails.normalize} first. */
	List<Lead> findByEmail(String email);

	/** The email of an open lead (new, contacted or qualified) that already uses one of these emails. */
	@org.springframework.data.jpa.repository.Query("select lower(l.email) from Lead l where l.email in :emails and l.status in (com.nexus.crm.leads.Lead.Status.NEW, com.nexus.crm.leads.Lead.Status.CONTACTED, com.nexus.crm.leads.Lead.Status.QUALIFIED)")
	List<String> findOpenEmails(@org.springframework.data.repository.query.Param("emails") java.util.Collection<String> emails);

}
