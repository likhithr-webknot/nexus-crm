package com.nexus.crm.search.dto;

import java.util.UUID;

/** {@code href} is a path in the frontend. */
public record Hit(
	String type,
	UUID id,
	String title,
	String subtitle,
	String href
) {
}
