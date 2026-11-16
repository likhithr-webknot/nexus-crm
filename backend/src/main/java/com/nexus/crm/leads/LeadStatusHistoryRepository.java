package com.nexus.crm.leads;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadStatusHistoryRepository extends JpaRepository<LeadStatusHistory, Long> {

	List<LeadStatusHistory> findByLeadIdOrderByChangedAtAscIdAsc(UUID leadId);

}
