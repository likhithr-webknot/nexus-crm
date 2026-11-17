package com.nexus.crm.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Writes the few lines of iCalendar (RFC 5545) that the follow-up feed needs. Lines end in CRLF and are folded at 75 bytes. */
final class IcsWriter {

	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

	private static final int MAX_LINE_BYTES = 75;

	private final StringBuilder out = new StringBuilder();

	IcsWriter line(String name, String value) {
		fold(name + ":" + value);
		return this;
	}

	/** A text value: backslash, semicolon, comma and line breaks are escaped, other control characters dropped. */
	IcsWriter text(String name, String value) {
		return line(name, escape(value));
	}

	IcsWriter time(String name, Instant value) {
		return line(name, UTC.format(value));
	}

	String build() {
		return this.out.toString();
	}

	static String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder clean = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> clean.append("\\\\");
				case ';' -> clean.append("\\;");
				case ',' -> clean.append("\\,");
				case '\n' -> clean.append("\\n");
				case '\r' -> {
					// A CRLF pair becomes one \n, which the \n case already wrote.
					if (i + 1 >= value.length() || value.charAt(i + 1) != '\n') {
						clean.append("\\n");
					}
				}
				default -> {
					if (c >= 0x20 && c != 0x7f) {
						clean.append(c);
					}
				}
			}
		}
		return clean.toString();
	}

	/** Splits a long line into 75-byte pieces. Each continuation starts with one space. A character is never cut in half. */
	private void fold(String line) {
		StringBuilder current = new StringBuilder();
		int bytes = 0;
		int limit = MAX_LINE_BYTES;
		for (int i = 0; i < line.length(); ) {
			int codePoint = line.codePointAt(i);
			String piece = new String(Character.toChars(codePoint));
			int size = piece.getBytes(StandardCharsets.UTF_8).length;
			if (bytes + size > limit) {
				this.out.append(current).append("\r\n");
				current = new StringBuilder(" ");
				bytes = 1;
				limit = MAX_LINE_BYTES;
			}
			current.append(piece);
			bytes += size;
			i += Character.charCount(codePoint);
		}
		this.out.append(current).append("\r\n");
	}

}
