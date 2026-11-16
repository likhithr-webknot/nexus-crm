package com.nexus.crm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailServiceTests {

	@Test
	void explainsHowToResolveGmailPermissionDenied() {
		String message = MailService.rejectionMessage(403, "PERMISSION_DENIED");

		assertThat(message).contains("Reconnect the mailbox", "Gmail API", "Workspace admin", "no email was sent");
	}

}
