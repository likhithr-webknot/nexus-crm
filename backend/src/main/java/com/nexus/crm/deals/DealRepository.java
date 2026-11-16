package com.nexus.crm.deals;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DealRepository extends JpaRepository<Deal, UUID>, JpaSpecificationExecutor<Deal> {

	List<Deal> findByCompanyId(UUID companyId);

}
