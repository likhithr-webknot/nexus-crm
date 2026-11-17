package com.nexus.crm.mail;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google reports the permissions a user granted as one space-separated string. A permission is present only when it
 * equals one of those words. Checking with {@code String.contains} would also match a longer scope that merely starts
 * with the same text (for example {@code …/calendar.events.readonly} for {@code …/calendar.events}).
 */
final class GoogleScopes {

	private GoogleScopes() {
	}

	static Set<String> parse(String scopes) {
		if (scopes == null || scopes.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(scopes.trim().split("\\s+")).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	static boolean has(String scopes, String scope) {
		return parse(scopes).contains(scope);
	}

	/** The same list without {@code scope}. */
	static String without(String scopes, String scope) {
		return parse(scopes).stream().filter((s) -> !s.equals(scope)).collect(Collectors.joining(" "));
	}

	/** True when at least one of the permissions this app uses for its features is still present. */
	static boolean hasAnyFeature(String scopes) {
		Set<String> granted = parse(scopes);
		return granted.contains(GoogleApi.GMAIL_SEND_SCOPE) || granted.contains(GoogleApi.CALENDAR_SCOPE)
				|| granted.contains(GoogleApi.GMAIL_READ_SCOPE);
	}

}
