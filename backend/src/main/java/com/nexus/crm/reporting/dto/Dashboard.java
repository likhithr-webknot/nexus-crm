package com.nexus.crm.reporting.dto;

import java.util.List;

public record Dashboard(
	List<Kpi> kpis,
	List<SourceShare> leadSources,
	List<StageTotal> pipeline
) {
}
