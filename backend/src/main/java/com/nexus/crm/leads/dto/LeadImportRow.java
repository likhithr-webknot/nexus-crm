package com.nexus.crm.leads.dto;

/** One row of an import, already read from the CSV by the browser. The server checks it again. */
public record LeadImportRow(
	String name,
	String email,
	String company,
	String phone,
	String source,
	String value,
	String currency
) {
}
