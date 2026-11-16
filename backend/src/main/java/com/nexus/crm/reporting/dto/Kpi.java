package com.nexus.crm.reporting.dto;

public record Kpi(
	String id,
	double value,
	Double change,
	double[] trend
) {
}
