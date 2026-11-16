package com.nexus.crm.reporting.dto;

import java.util.UUID;

public record TeamRow(
	UUID userId,
	String name,
	long leads,
	long dealsWon,
	double revenue,
	double conversion
) {
}
