package com.nexus.crm.trash.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexus.crm.trash.TrashType;

/** A deleted record as it is shown in the recycle bin. */
public record TrashItem(
	TrashType type,
	UUID id,
	String name,
	Instant deletedAt,
	String ownerName
) {
}
