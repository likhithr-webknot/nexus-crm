package com.nexus.crm.notifications.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
	UUID id,
	String type,
	String title,
	String link,
	boolean read,
	Instant createdAt
) {
}
