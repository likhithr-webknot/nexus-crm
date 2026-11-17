package com.nexus.crm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexus.crm.mail.MimeMessageBuilder.Recipients;

/** The raw email handed to Gmail: headers, the HTML part and the optional signature image. */
class MimeMessageBuilderTests {

	private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	private static final Recipients TO = new Recipients("priya@client.example", List.of(), List.of());

	@Test
	void aPlainMessageHasOneBase64HtmlPart() {
		String message = MimeMessageBuilder.build("me@acme.example", TO, "Hello", "Hi <b>there</b>\nBye", ID, null);

		assertThat(message).startsWith("From: me@acme.example\r\nTo: priya@client.example\r\n");
		assertThat(message).contains("Message-ID: <" + ID + "@nexus-crm.local>\r\nMIME-Version: 1.0\r\n");
		assertThat(message).contains("Content-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n");
		assertThat(message).doesNotContain("multipart");
		assertThat(htmlOf(message)).contains("Hi &lt;b&gt;there&lt;/b&gt;<br>Bye").doesNotContain("cid:");
	}

	@Test
	void theSubjectIsEncodedSoNonAsciiTextAndLineBreaksCannotBreakTheHeader() {
		String message = MimeMessageBuilder.build("me@acme.example", TO, "Café ✓", "x", ID, null);

		String encoded = Base64.getEncoder().encodeToString("Café ✓".getBytes(StandardCharsets.UTF_8));
		assertThat(message).contains("Subject: =?UTF-8?B?" + encoded + "?=\r\n");
	}

	@Test
	void aSignatureMakesItMultipartWithTheImageLinkedByContentId() {
		MailBannerService.Rendered signature = new MailBannerService.Rendered(new byte[] { 1, 2, 3 }, "Priya, Sales");

		String message = MimeMessageBuilder.build("me@acme.example", TO, "Hello", "Body", ID, signature);

		String boundary = "nexus_11111111222233334444555555555555";
		assertThat(message).contains("Content-Type: multipart/related; boundary=\"" + boundary + "\"\r\n\r\n");
		assertThat(message).contains("Content-ID: <nexus-banner>").contains("Content-Type: image/png");
		assertThat(message).endsWith("\r\n--" + boundary + "--\r\n");
		assertThat(htmlOf(message)).contains("src=\"cid:nexus-banner\"").contains("alt=\"Priya, Sales\"");
	}

	@Test
	void theSignatureTextCannotBreakOutOfTheImageTag() {
		MailBannerService.Rendered signature = new MailBannerService.Rendered(new byte[] { 1 }, "\"><script>x</script>");

		String message = MimeMessageBuilder.build("me@acme.example", TO, "Hello", "Body", ID, signature);

		assertThat(htmlOf(message)).doesNotContain("<script>").contains("&quot;&gt;&lt;script&gt;");
	}

	/** The first base64 part of a message, decoded back to HTML. */
	private static String htmlOf(String message) {
		String afterHeaders = message.substring(message.indexOf("Content-Transfer-Encoding: base64\r\n\r\n") + "Content-Transfer-Encoding: base64\r\n\r\n".length());
		int end = afterHeaders.indexOf("\r\n--");
		String encoded = (end < 0) ? afterHeaders : afterHeaders.substring(0, end);
		return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
	}

}
