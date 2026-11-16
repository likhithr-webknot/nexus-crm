package com.nexus.crm.reporting.dto;

public record StageTotal(
	String stageCode,
	String name,
	long count,
	double amount,
	long unconverted
) {
}
