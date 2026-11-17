package com.nexus.crm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A permission counts only when it is a whole word of Google's list, never a part of a longer one. */
class GoogleScopesTests {

	private static final String CALENDAR = GoogleApi.CALENDAR_SCOPE;

	@Test
	void aScopeIsFoundWhenItIsOneOfTheWords() {
		String granted = "openid email " + CALENDAR + " " + GoogleApi.GMAIL_SEND_SCOPE;

		assertThat(GoogleScopes.has(granted, CALENDAR)).isTrue();
		assertThat(GoogleScopes.has(granted, GoogleApi.GMAIL_SEND_SCOPE)).isTrue();
		assertThat(GoogleScopes.has(granted, GoogleApi.GMAIL_READ_SCOPE)).isFalse();
	}

	@Test
	void aLongerScopeThatStartsTheSameWayIsNotAMatch() {
		assertThat(GoogleScopes.has(CALENDAR + ".readonly", CALENDAR)).isFalse();
		assertThat(GoogleScopes.has("https://www.googleapis.com/auth/gmail.sendonly", GoogleApi.GMAIL_SEND_SCOPE)).isFalse();
		assertThat(GoogleScopes.has("prefix-" + CALENDAR, CALENDAR)).isFalse();
	}

	@Test
	void emptyOrMissingScopesGrantNothing() {
		assertThat(GoogleScopes.has(null, CALENDAR)).isFalse();
		assertThat(GoogleScopes.has("", CALENDAR)).isFalse();
		assertThat(GoogleScopes.has("   ", CALENDAR)).isFalse();
	}

	@Test
	void extraSpacesBetweenWordsAreFine() {
		assertThat(GoogleScopes.has("openid   " + CALENDAR + "  email", CALENDAR)).isTrue();
	}

	@Test
	void withoutRemovesOnlyThatExactScope() {
		String granted = CALENDAR + " " + CALENDAR + ".readonly " + GoogleApi.GMAIL_SEND_SCOPE;

		assertThat(GoogleScopes.without(granted, CALENDAR)).isEqualTo(CALENDAR + ".readonly " + GoogleApi.GMAIL_SEND_SCOPE);
	}

	@Test
	void aConnectionIsStillNeededOnlyWhileOneOfTheThreeFeatureScopesRemains() {
		assertThat(GoogleScopes.hasAnyFeature("openid email " + GoogleApi.GMAIL_READ_SCOPE)).isTrue();
		assertThat(GoogleScopes.hasAnyFeature("openid email")).isFalse();
		assertThat(GoogleScopes.hasAnyFeature(CALENDAR + ".readonly")).isFalse();
		assertThat(GoogleScopes.hasAnyFeature("")).isFalse();
	}

}
