package com.nexus.crm.activities;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityRepository extends JpaRepository<Activity, UUID>, JpaSpecificationExecutor<Activity> {

	List<Activity> findByLeadIdOrderByOccurredAtDesc(UUID leadId, Pageable page);

	List<Activity> findByContactIdOrderByOccurredAtDesc(UUID contactId, Pageable page);

	List<Activity> findByDealIdOrderByOccurredAtDesc(UUID dealId, Pageable page);

}
