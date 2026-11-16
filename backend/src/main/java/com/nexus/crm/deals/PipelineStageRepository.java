package com.nexus.crm.deals;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, String> {

	List<PipelineStage> findAllByOrderByPositionAsc();

}
