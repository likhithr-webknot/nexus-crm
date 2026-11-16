package com.nexus.crm.shared;

/** A person or deal as it is shown in lists: its name, and the company it belongs to (may be null). */
public record Label(
	String name,
	String company
) {
}
