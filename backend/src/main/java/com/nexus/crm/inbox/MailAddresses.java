package com.nexus.crm.inbox;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls plain email addresses out of From/To/Cc header values such as {@code "Priya, S." <priya@acme.example>, bob@acme.example}. */
final class MailAddresses {

	private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");

	private MailAddresses() {
	}

	/** Lower-case addresses in order, without repeats. A display name that looks like an address is read as an address, which is harmless. */
	static Set<String> parse(String header) {
		Set<String> found = new LinkedHashSet<>();
		if (header == null) {
			return found;
		}
		Matcher matcher = ADDRESS.matcher(header);
		while (matcher.find()) {
			found.add(matcher.group().toLowerCase(Locale.ROOT));
		}
		return found;
	}

}
