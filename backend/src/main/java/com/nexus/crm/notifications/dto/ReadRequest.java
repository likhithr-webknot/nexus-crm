package com.nexus.crm.notifications.dto;

import java.util.List;
import java.util.UUID;

/** The IDs to mark as read. Leave it out or empty to mark everything. */
public record ReadRequest(
	List<UUID> ids
) {
}
