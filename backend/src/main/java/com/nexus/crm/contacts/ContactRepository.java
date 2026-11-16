package com.nexus.crm.contacts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

	/** Pass the email through {@code Emails.normalize} first. */
	Optional<Contact> findByEmail(String email);

	List<Contact> findByCompanyId(UUID companyId);

	long countByCompanyId(UUID companyId);

}
