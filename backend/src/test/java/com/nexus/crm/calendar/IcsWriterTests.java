package com.nexus.crm.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** The calendar file format: escaping, line folding and the time format. */
class IcsWriterTests {

	@Test
	void textIsEscapedSoItCannotBreakOutOfItsLine() {
		assertThat(IcsWriter.escape("a,b;c\\d")).isEqualTo("a\\,b\\;c\\\\d");
		assertThat(IcsWriter.escape("one\r\ntwo\nthree\rfour")).isEqualTo("one\\ntwo\\nthree\\nfour");
	}

	@Test
	void aTitleCannotInjectAnotherCalendarLine() {
		String ics = new IcsWriter().text("SUMMARY", "Call\r\nEND:VCALENDAR\r\nBEGIN:VEVENT").build();

		assertThat(ics.split("\r\n")).hasSize(1);
		assertThat(ics).startsWith("SUMMARY:Call\\nEND:VCALENDAR");
	}

	@Test
	void controlCharactersAreDropped() {
		assertThat(IcsWriter.escape("a\u0000b\u0007c\u007fd")).isEqualTo("abcd");
	}

	@Test
	void longLinesAreFoldedAt75BytesAndNeverInsideACharacter() {
		String ics = new IcsWriter().line("DESCRIPTION", "é".repeat(100)).build();

		String[] lines = ics.split("\r\n");
		assertThat(lines).hasSizeGreaterThan(1);
		for (String line : lines) {
			assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
		}
		for (int i = 1; i < lines.length; i++) {
			assertThat(lines[i]).startsWith(" ");
		}
		// Unfolding gives back the original value.
		assertThat(ics.replace("\r\n ", "").trim()).isEqualTo("DESCRIPTION:" + "é".repeat(100));
	}

	@Test
	void everyLineEndsWithCrlf() {
		String ics = new IcsWriter().line("BEGIN", "VCALENDAR").line("END", "VCALENDAR").build();

		assertThat(ics).isEqualTo("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
	}

	@Test
	void timesAreWrittenInUtc() {
		String ics = new IcsWriter().time("DTSTART", Instant.parse("2026-03-05T09:08:07Z")).build();

		assertThat(ics).isEqualTo("DTSTART:20260305T090807Z\r\n");
	}

}
