package com.nexus.crm.modules;

import java.util.Set;

public enum ModuleKind {
	CAMPAIGNS("campaigns", "Campaign", Set.of("Draft", "Scheduled", "Active", "Completed")),
	PRODUCTS("products", "Product", Set.of("Active", "Archived")),
	QUOTES("quotes", "Quote", Set.of("Draft", "In review", "Approved", "Declined")),
	COMMUNICATIONS("communications", "Communication", Set.of("Draft", "Sending", "Sent", "Failed", "Unknown")),
	AUTOMATIONS("automations", "Automation", Set.of("Draft", "Published", "Paused"));

	private final String path;
	private final String entity;
	private final Set<String> statuses;

	ModuleKind(String path, String entity, Set<String> statuses) {
		this.path = path;
		this.entity = entity;
		this.statuses = statuses;
	}

	public String path() { return this.path; }
	public String entity() { return this.entity; }
	public boolean allows(String status) { return this.statuses.contains(status); }

	public static ModuleKind fromPath(String path) {
		for (ModuleKind kind : values()) if (kind.path.equals(path)) return kind;
		throw new com.nexus.crm.shared.error.ApiException(com.nexus.crm.shared.error.ErrorCode.NOT_FOUND, "Workspace not found.");
	}
}
