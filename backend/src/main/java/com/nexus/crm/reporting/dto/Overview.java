package com.nexus.crm.reporting.dto;

import java.util.List;

public record Overview(
	List<FunnelStep> funnel,
	List<SourceRevenue> revenueBySource,
	List<TeamRow> team,
	double averageDealSize,
	List<Kpi> kpis
) {
}
