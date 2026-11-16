package com.nexus.crm.contacts;

import java.util.Optional;
import java.util.UUID;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

	List<Company> findByIdIn(Collection<UUID> ids);

	List<Company> findByNameIgnoreCaseAndOwnerId(String name, UUID ownerId);

	/** Pass the domain through {@code Emails.normalize} first. */
	Optional<Company> findByDomain(String domain);

}
