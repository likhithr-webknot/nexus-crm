package com.nexus.crm.reporting.dto;

public record SourceShare(
	String source,
	long count,
	double share
) {
}
