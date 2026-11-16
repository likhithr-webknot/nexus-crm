package com.nexus.crm.deals.dto;

import java.util.Map;
import java.util.UUID;

public record DealNames(
	Map<UUID, String> companies,
	Map<UUID, String> contacts,
	Map<UUID, String> owners
) {
}
