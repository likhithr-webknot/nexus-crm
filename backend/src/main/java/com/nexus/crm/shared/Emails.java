package com.nexus.crm.shared;

import java.util.Locale;

/**
 * Email columns are case-insensitive in the database, but a query that passes
 * the email as plain text compares it case-sensitively. So we store every email
 * in lower case and look it up in lower case.
 */
public final class Emails {

	private Emails() {
	}

	/** Trims and lower-cases an email. A missing or blank email becomes null, so it never clashes with another blank one. */
	public static String normalize(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
