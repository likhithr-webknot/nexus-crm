package com.nexus.crm.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailsTests {

	@ParameterizedTest
	@ValueSource(strings = { "a@example.com", "first.last+tag@sub.example.co.in", "o'brien@example.org", "x@xn--p1ai.xn--p1ai" })
	void ordinaryAddressesPass(String email) {
		assertThat(Emails.isValid(email)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"a@b", "a@b.c", "@example.com", "a@@example.com", "a b@example.com", " a@example.com", "a@example.com ",
			// A second recipient in one field. The old check accepted all of these.
			"a@example.com,b@example.com", "a@example.com;b@example.com", "a@example.com b@example.com",
			"Name <a@example.com>", "\"Name\" a@example.com",
			// Header injection.
			"a@example.com\r\nBcc: evil@example.com", "a@example.com\nBcc: evil@example.com",
			// Dots in the wrong place, a bad domain.
			".a@example.com", "a.@example.com", "a..b@example.com", "a@-example.com", "a@example..com", "a@example.com-",
			"a@[127.0.0.1]" })
	void anythingElseIsRefused(String email) {
		assertThat(Emails.isValid(email)).isFalse();
	}

	@Test
	void lengthLimitsApply() {
		assertThat(Emails.isValid("a".repeat(64) + "@example.com")).isTrue();
		assertThat(Emails.isValid("a".repeat(65) + "@example.com")).isFalse();
		assertThat(Emails.isValid("a@" + "b".repeat(250) + ".com")).isFalse();
	}

	@Test
	void aLongRepeatedInputIsRejectedQuickly() {
		long start = System.nanoTime();
		assertThat(Emails.isValid("a@" + "a-".repeat(120) + "!")).isFalse();
		assertThat(System.nanoTime() - start).isLessThan(200_000_000L);
	}

}
