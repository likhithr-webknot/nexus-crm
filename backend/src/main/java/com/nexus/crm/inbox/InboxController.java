package com.nexus.crm.inbox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.contacts.ContactService;
import com.nexus.crm.deals.Deal;
import com.nexus.crm.deals.DealService;
import com.nexus.crm.leads.LeadService;
import com.nexus.crm.mail.MailService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** Inbox sync status and settings, and the emails shown on a contact, lead or deal. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class InboxController {

	private static final int LIMIT = 50;

	private final JdbcClient jdbc;

	private final MailService mail;

	private final ContactService contacts;

	private final LeadService leads;

	private final DealService deals;

	private final AuditService audit;

	private final TransactionTemplate transactions;

	record InboxStatus(boolean configured, boolean connected, boolean needsReconnect, String email, boolean backfilling, OffsetDateTime lastSyncedAt,
			String lastError, long imported) {
	}

	/** One email on a record. {@code gmailUrl} is only set for the person whose mailbox it came from. */
	record EmailItem(UUID id, String direction, String from, String to, String subject, String snippet, OffsetDateTime sentAt, String mailbox,
			String gmailUrl) {
	}

	@GetMapping("/inbox/status")
	InboxStatus status(@AuthenticationPrincipal CurrentUser user) {
		MailService.Status account = this.mail.status(user.id());
		var state = this.jdbc.sql("select backfilling, last_synced_at, last_error from mail_sync_state where user_id = :user").param("user", user.id())
			.query((rs, i) -> new Object[] { rs.getBoolean(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3) }).optional();
		boolean syncing = state.isPresent();
		long imported = this.jdbc.sql("select count(*) from email_message where user_id = :user").param("user", user.id()).query(Long.class).single();
		return new InboxStatus(account.configured(), syncing && account.inbox(), syncing && account.needsReconnect(), account.email(),
				syncing && (Boolean) state.get()[0], syncing ? (OffsetDateTime) state.get()[1] : null, syncing ? (String) state.get()[2] : null, imported);
	}

	/** Turns inbox sync off. Emails already copied stay on their records unless {@code deleteImported} is true. */
	@DeleteMapping("/inbox")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "false") boolean deleteImported) {
		this.transactions.executeWithoutResult((tx) -> {
			boolean was = this.jdbc.sql("delete from mail_sync_state where user_id = :user").param("user", user.id()).update() > 0;
			int removed = deleteImported ? this.jdbc.sql("delete from email_message where user_id = :user").param("user", user.id()).update() : 0;
			if (was || removed > 0) {
				this.audit.record(user.id(), "InboxSync", user.id(), AuditLog.Action.DELETE, null);
			}
		});
	}

	/** The emails on one record, newest first. The caller must be allowed to open that record. */
	@GetMapping("/emails")
	List<EmailItem> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) UUID contactId,
			@RequestParam(required = false) UUID leadId, @RequestParam(required = false) UUID dealId) {
		if ((contactId == null ? 0 : 1) + (leadId == null ? 0 : 1) + (dealId == null ? 0 : 1) != 1) {
			throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Give exactly one of contactId, leadId or dealId.");
		}
		UUID contact = contactId;
		UUID lead = leadId;
		if (contactId != null) {
			this.contacts.get(user, contactId);
		}
		else if (leadId != null) {
			this.leads.get(user, leadId);
		}
		else {
			// A deal shows the mail of its main contact, and of the lead it came from.
			Deal deal = this.deals.get(user, dealId);
			contact = deal.getPrimaryContactId();
			lead = deal.getLeadId();
		}
		return this.jdbc.sql("""
				select * from (
				  select distinct on (coalesce(e.rfc_message_id, e.id::text)) e.id, e.direction, e.from_addr, e.to_addrs, e.subject, e.snippet,
				         e.sent_at, e.thread_id, e.user_id, u.display_name
				  from email_message e join app_user u on u.id = e.user_id
				  where e.contact_id = :contact or e.lead_id = :lead
				  order by coalesce(e.rfc_message_id, e.id::text), e.sent_at desc) shown
				order by sent_at desc limit :limit
				""")
			.param("contact", contact).param("lead", lead).param("limit", LIMIT)
			.query((rs, i) -> new EmailItem(rs.getObject("id", UUID.class), rs.getString("direction"), rs.getString("from_addr"),
					rs.getString("to_addrs"), rs.getString("subject"), rs.getString("snippet"), rs.getObject("sent_at", OffsetDateTime.class),
					rs.getString("display_name"),
					(user.id().equals(rs.getObject("user_id", UUID.class)) && rs.getString("thread_id") != null)
							? "https://mail.google.com/mail/u/0/#all/" + rs.getString("thread_id") : null))
			.list();
	}

}
