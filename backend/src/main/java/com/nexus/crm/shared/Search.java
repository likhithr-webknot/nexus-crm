package com.nexus.crm.shared;

import java.util.Locale;

public final class Search {

	private Search() {
	}

	/**
	 * A lower-case "contains" pattern for SQL LIKE. The user's own % and _ are escaped,
	 * so they match literally instead of acting as wildcards.
	 */
	public static String pattern(String query) {
		String escaped = query.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
