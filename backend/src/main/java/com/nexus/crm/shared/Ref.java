package com.nexus.crm.shared;

import java.util.UUID;

/** A link to another record as it is shown in lists: its ID and its name. */
public record Ref(
	UUID id,
	String name
) {
}
