package com.nexus.crm.deals;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DealStageHistoryRepository extends JpaRepository<DealStageHistory, Long> {

	List<DealStageHistory> findByDealIdOrderByChangedAtAscIdAsc(UUID dealId);

}
