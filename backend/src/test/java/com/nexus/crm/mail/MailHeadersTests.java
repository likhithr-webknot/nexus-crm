package com.nexus.crm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MailHeadersTests {

	@Test
	void toOnlyHasNoCcOrBccLine() {
		String headers = MailService.addressHeaders(new MailService.Recipients("a@example.com", List.of(), List.of()));

		assertThat(headers).isEqualTo("To: a@example.com\r\n");
	}

	@Test
	void ccAndBccGetTheirOwnFoldedLines() {
		String headers = MailService.addressHeaders(new MailService.Recipients("a@example.com",
				List.of("b@example.com", "c@example.com"), List.of("d@example.com")));

		assertThat(headers).isEqualTo("To: a@example.com\r\nCc: b@example.com,\r\n c@example.com\r\nBcc: d@example.com\r\n");
	}

	@Test
	void everyLineStaysFarBelowTheMailLineLimit() {
		List<String> twenty = java.util.stream.IntStream.range(0, 20).mapToObj(i -> "x".repeat(60) + i + "@example.com").toList();

		String headers = MailService.addressHeaders(new MailService.Recipients("a@example.com", twenty, twenty));

		assertThat(headers.split("\r\n")).allSatisfy(line -> assertThat(line.length()).isLessThan(998));
	}

}
