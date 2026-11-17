package com.nexus.crm.tags.dto;

import java.util.UUID;

/** A tag as shown on a record. */
public record TagRef(UUID id, String name, String color) {
}
