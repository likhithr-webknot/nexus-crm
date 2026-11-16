package com.nexus.crm.trash;

/**
 * The kinds of record that can be deleted and restored. The table and column names are fixed text, never
 * taken from a request, so they are safe to put in SQL.
 */
public enum TrashType {

	LEAD("lead", "trim(first_name || ' ' || last_name)", "Lead"),
	CONTACT("contact", "trim(first_name || ' ' || last_name)", "Contact"),
	COMPANY("company", "name", "Company"),
	DEAL("deal", "name", "Deal"),
	CAMPAIGN("crm_module_record", "name", "Campaign", " and kind='campaigns'"),
	PRODUCT("crm_module_record", "name", "Product", " and kind='products'"),
	QUOTE("crm_module_record", "name", "Quote", " and kind='quotes'"),
	COMMUNICATION("crm_module_record", "name", "Communication", " and kind='communications'"),
	AUTOMATION("crm_module_record", "name", "Automation", " and kind='automations'");

	private final String table;

	private final String nameSql;

	private final String entityName;
	private final String kindFilter;

	TrashType(String table, String nameSql, String entityName) {
		this(table, nameSql, entityName, "");
	}

	TrashType(String table, String nameSql, String entityName, String kindFilter) {
		this.table = table;
		this.nameSql = nameSql;
		this.entityName = entityName;
		this.kindFilter = kindFilter;
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

	public String kindFilter() { return kindFilter; }

	/** A lead may have no owner (the unassigned queue), so deleted unassigned leads are visible to every manager. */
	public boolean ownerMayBeEmpty() {
		return this == LEAD;
	}

}
