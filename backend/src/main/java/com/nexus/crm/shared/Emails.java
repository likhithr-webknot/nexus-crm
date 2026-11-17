package com.nexus.crm.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/** Email columns are case-insensitive in the database, but a query that passes the email as plain text compares it case-sensitively. */
public final class Emails {

	/** One plain address: no spaces, commas, angle brackets, quotes or line breaks, so it cannot add a recipient or a mail header. */
	private static final Pattern ADDRESS = Pattern.compile(
			"[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
					+ "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])");

	private static final int MAX_LENGTH = 254;

	private static final int MAX_LOCAL_PART = 64;

	private Emails() {
	}

	/** Trims and lower-cases an email. A missing or blank email becomes null, so it never clashes with another blank one. */
	public static String normalize(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.strip().toLowerCase(Locale.ROOT);
	}

	/** True when the text is exactly one valid address. It does not trim, so pass in what will be sent. */
	public static boolean isValid(String email) {
		return email != null && email.length() <= MAX_LENGTH && email.indexOf('@') <= MAX_LOCAL_PART
				&& ADDRESS.matcher(email).matches();
	}

}
