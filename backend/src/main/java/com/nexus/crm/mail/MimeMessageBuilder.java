package com.nexus.crm.mail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.nexus.crm.shared.Emails;

/**
 * Builds the raw email that is handed to Gmail. Nothing here talks to the network or the database,
 * so it can be tested on its own.
 */
final class MimeMessageBuilder {

	private static final String CRLF = "\r\n";

	private MimeMessageBuilder() {
	}

	/** Who a message goes to. Each address passed {@link Emails}, so none can add a header line or a second recipient. */
	record Recipients(String to, List<String> cc, List<String> bcc) {
	}

	/**
	 * The To, Cc and Bcc lines. Each address goes on its own folded line, so a long list stays under the 1000 character
	 * limit. Gmail delivers to Bcc and removes that header from the copies the others receive.
	 */
	static String addressHeaders(Recipients recipients) {
		StringBuilder headers = new StringBuilder("To: ").append(recipients.to()).append(CRLF);
		if (!recipients.cc().isEmpty()) {
			headers.append("Cc: ").append(String.join(",\r\n ", recipients.cc())).append(CRLF);
		}
		if (!recipients.bcc().isEmpty()) {
			headers.append("Bcc: ").append(String.join(",\r\n ", recipients.bcc())).append(CRLF);
		}
		return headers.toString();
	}

	/** The message as the sender wrote it, with their signature image inside it when they have one. */
	static String build(String from, Recipients to, String subject, String body, UUID messageId,
			MailBannerService.Rendered signature) {
		return (signature == null) ? plain(from, to, subject, body, messageId)
				: withSignature(from, to, subject, body, messageId, signature);
	}

	/** A single HTML part. */
	private static String plain(String from, Recipients to, String subject, String body, UUID messageId) {
		return commonHeaders(from, to, subject, messageId)
				+ "Content-Type: text/html; charset=UTF-8" + CRLF
				+ "Content-Transfer-Encoding: base64" + CRLF + CRLF
				+ wrapBase64(html(body, null).getBytes(StandardCharsets.UTF_8));
	}

	/** An HTML part plus the signature image, which the HTML shows through a {@code cid:} link. */
	private static String withSignature(String from, Recipients to, String subject, String body, UUID messageId,
			MailBannerService.Rendered signature) {
		String boundary = "nexus_" + messageId.toString().replace("-", "");
		String html = html(body, signature.alt());

		return commonHeaders(from, to, subject, messageId)
				+ "Content-Type: multipart/related; boundary=\"" + boundary + "\"" + CRLF + CRLF
				// Part 1: the text.
				+ "--" + boundary + CRLF
				+ "Content-Type: text/html; charset=UTF-8" + CRLF
				+ "Content-Transfer-Encoding: base64" + CRLF + CRLF
				+ wrapBase64(html.getBytes(StandardCharsets.UTF_8))
				// Part 2: the image.
				+ CRLF + "--" + boundary + CRLF
				+ "Content-Type: image/png" + CRLF
				+ "Content-ID: <nexus-banner>" + CRLF
				+ "Content-Disposition: inline; filename=\"banner.png\"" + CRLF
				+ "Content-Transfer-Encoding: base64" + CRLF + CRLF
				+ wrapBase64(signature.png())
				+ CRLF + "--" + boundary + "--" + CRLF;
	}

	private static String commonHeaders(String from, Recipients to, String subject, UUID messageId) {
		String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
		return "From: " + from + CRLF
				+ addressHeaders(to)
				+ "Subject: " + encodedSubject + CRLF
				+ "Message-ID: <" + messageId + "@nexus-crm.local>" + CRLF
				+ "MIME-Version: 1.0" + CRLF;
	}

	/** Base64 in lines of 76 characters, as mail requires. */
	private static String wrapBase64(byte[] bytes) {
		return Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII)).encodeToString(bytes);
	}

	/**
	 * The message as HTML: readable text in a 600px column, then the signature image when there is one.
	 * No text colours are set, so a mail client's dark mode can restyle the text itself.
	 */
	private static String html(String body, String signatureAlt) {
		String signature = (signatureAlt == null) ? "" : signatureHtml(signatureAlt);
		return "<!doctype html><html><body style=\"margin:0;padding:0\"><div style=\"max-width:600px;"
				+ "font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.6\">"
				+ escape(body).replace("\n", "<br>")
				+ signature
				+ "</div></body></html>";
	}

	private static String signatureHtml(String alt) {
		int width = MailBannerService.SHOWN_WIDTH;
		return "<div style=\"margin-top:28px\"><img src=\"cid:nexus-banner\" width=\"" + width + "\" alt=\"" + escape(alt)
				+ "\" style=\"display:block;width:100%;max-width:" + width + "px;height:auto;border:0;border-radius:8px\"></div>";
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
