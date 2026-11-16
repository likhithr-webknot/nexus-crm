package com.nexus.crm.trash;

/**
 * The kinds of record that can be deleted and restored. The table and column names are fixed text, never
 * taken from a request, so they are safe to put in SQL.
 */
public enum TrashType {

	LEAD("lead", "trim(first_name || ' ' || last_name)", "Lead"),
	CONTACT("contact", "trim(first_name || ' ' || last_name)", "Contact"),
	COMPANY("company", "name", "Company"),
	DEAL("deal", "name", "Deal");

	private final String table;

	private final String nameSql;

	private final String entityName;

	TrashType(String table, String nameSql, String entityName) {
		this.table = table;
		this.nameSql = nameSql;
		this.entityName = entityName;
	}

	public String table() {
		return this.table;
	}

	/** SQL for the name shown in the list. */
	public String nameSql() {
		return this.nameSql;
	}

	/** The name used in the audit log, for example "Lead". */
	public String entityName() {
		return this.entityName;
	}

	/** A lead may have no owner (the unassigned queue), so deleted unassigned leads are visible to every manager. */
	public boolean ownerMayBeEmpty() {
		return this == LEAD;
	}

}
