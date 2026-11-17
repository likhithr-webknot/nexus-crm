package com.nexus.crm.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailAddressesTests {

	@Test
	void findsAddressesWithAndWithoutNames() {
		assertThat(MailAddresses.parse("Priya S <Priya@Acme.Example>, bob@acme.example")).containsExactly("priya@acme.example", "bob@acme.example");
	}

	@Test
	void aCommaInsideAQuotedNameDoesNotSplitTheAddress() {
		assertThat(MailAddresses.parse("\"Sharma, Priya\" <priya@acme.example>")).containsExactly("priya@acme.example");
	}

	@Test
	void repeatsAreDroppedAndOrderIsKept() {
		assertThat(MailAddresses.parse("a@x.example, B@x.example, a@X.example")).containsExactly("a@x.example", "b@x.example");
	}

	@Test
	void nothingIsFoundInEmptyOrAddressFreeText() {
		assertThat(MailAddresses.parse(null)).isEmpty();
		assertThat(MailAddresses.parse("undisclosed-recipients:;")).isEmpty();
	}

	@Test
	void plusTagsAndSubdomainsAreKept() {
		assertThat(MailAddresses.parse("sales+eu@mail.corp.example")).containsExactly("sales+eu@mail.corp.example");
	}

}
