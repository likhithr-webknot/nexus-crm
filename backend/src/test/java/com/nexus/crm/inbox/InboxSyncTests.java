package com.nexus.crm.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.mail.GoogleApi;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.SecretBox;
import com.nexus.crm.support.IntegrationTest;

/** Reading a mailbox: only mail with a known contact or lead is kept, nothing doubles, and access follows the record. */
@TestPropertySource(properties = { "GOOGLE_CLIENT_ID=test-client", "GOOGLE_CLIENT_SECRET=test-secret",
		"NEXUS_MAIL_REDIRECT_URI=http://localhost:8080/api/v1/mail/callback",
		"NEXUS_MAIL_TOKEN_KEY=" + InboxSyncTests.TOKEN_KEY })
class InboxSyncTests extends IntegrationTest {

	static final String TOKEN_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@MockitoSpyBean
	private GoogleApi google;

	@Autowired
	private InboxSyncService sync;

	@Autowired
	private JdbcClient jdbc;

	private AppUser rep;

	private String repEmail;

	private Session session;

	private String contactEmail;

	private String contactId;

	/** What the pretend Gmail answers. The first key found in the request address wins. */
	private final Map<String, GoogleApi.Response> routes = new LinkedHashMap<>();

	private Instant clock = Instant.now();

	@BeforeEach
	void connectARep() {
		this.jdbc.sql("delete from mail_sync_state").update();
		reset(this.google);
		this.routes.clear();
		this.repEmail = uniqueEmail();
		this.rep = invite(this.repEmail, Role.REP);
		this.session = signIn(uniqueGoogleId(), this.repEmail);
		connectMailbox(this.rep, this.repEmail);

		this.contactEmail = "priya-" + UUID.randomUUID() + "@client.example";
		this.contactId = field(post(this.session, "/api/v1/contacts",
				"{\"firstName\":\"Priya\",\"lastName\":\"Shah\",\"email\":\"" + this.contactEmail + "\",\"companyName\":\"Client Co " + UUID.randomUUID() + "\"}"), "id");

		doReturn(true).when(this.google).configured();
		doReturn("token").when(this.google).accessToken(any(UUID.class), anyString());
		doAnswer((call) -> {
			String url = call.getArgument(1);
			return this.routes.entrySet().stream().filter((e) -> url.contains(e.getKey())).map(Map.Entry::getValue).findFirst()
				.orElse(new GoogleApi.Response(404, "{}"));
		}).when(this.google).call(eq("GET"), anyString(), eq("token"), any());
	}

	private void connectMailbox(AppUser user, String email) {
		this.jdbc.sql("insert into mail_connection (user_id, email, refresh_token_cipher, scopes) values (:user, :email, :cipher, :scopes)")
			.param("user", user.getId()).param("email", email).param("cipher", SecretBox.of(TOKEN_KEY, null).encrypt("saved-refresh-token"))
			.param("scopes", GoogleApi.GMAIL_READ_SCOPE).update();
		this.jdbc.sql("insert into mail_sync_state (user_id) values (:user)").param("user", user.getId()).update();
	}

	private MvcTestResult post(Session who, String uri, String body) {
		return withCsrf(this.mvc.post().uri(uri)).cookie(who.accessToken()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult get(Session who, String uri) {
		return this.mvc.get().uri(uri).cookie(who.accessToken()).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String field(MvcTestResult result, String name) {
		return json(result).replaceAll("^.*?\"" + name + "\":\"([^\"]+)\".*$", "$1");
	}

	private static String message(String id, String labels, String from, String to, String subject, String rfcId) {
		return "{\"id\":\"" + id + "\",\"threadId\":\"t-" + id + "\",\"labelIds\":" + labels + ",\"snippet\":\"Snippet of " + id + "\","
				+ "\"internalDate\":\"" + (Instant.now().toEpochMilli() - 60_000) + "\",\"payload\":{\"headers\":["
				+ "{\"name\":\"From\",\"value\":\"" + from + "\"},{\"name\":\"To\",\"value\":\"" + to + "\"},"
				+ "{\"name\":\"Subject\",\"value\":\"" + subject + "\"},{\"name\":\"Message-ID\",\"value\":\"<" + rfcId + ">\"}]}}";
	}

	private void gmailHas(String... messageIds) {
		StringBuilder list = new StringBuilder("{\"messages\":[");
		for (int i = 0; i < messageIds.length; i++) {
			list.append(i == 0 ? "" : ",").append("{\"id\":\"").append(messageIds[i]).append("\"}");
		}
		this.routes.put("/profile", new GoogleApi.Response(200, "{\"historyId\":\"100\"}"));
		this.routes.put("/messages?", new GoogleApi.Response(200, list.append("]}").toString()));
	}

	private void run() {
		this.clock = this.clock.plus(Duration.ofMinutes(10));
		this.sync.syncDue(this.clock);
	}

	private long stored() {
		return this.jdbc.sql("select count(*) from email_message where user_id = :user").param("user", this.rep.getId()).query(Long.class).single();
	}

	@Test
	void onlyMailWithAKnownContactIsKeptAndShownOnTheContact() {
		gmailHas("m1", "m2", "m3");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", "Priya <" + this.contactEmail + ">", this.repEmail, "Your quote", "a1@x")));
		this.routes.put("/messages/m2", new GoogleApi.Response(200, message("m2", "[\"SENT\"]", this.repEmail, this.contactEmail, "Re: Your quote", "a2@x")));
		this.routes.put("/messages/m3", new GoogleApi.Response(200, message("m3", "[\"INBOX\"]", "stranger@elsewhere.example", this.repEmail, "Buy now", "a3@x")));

		run();

		assertThat(stored()).isEqualTo(2);
		String shown = json(get(this.session, "/api/v1/emails?contactId=" + this.contactId));
		assertThat(shown).contains("Your quote").contains("Re: Your quote").contains("\"direction\":\"IN\"").contains("\"direction\":\"OUT\"")
			.contains("Snippet of m1").doesNotContain("Buy now").contains("mail.google.com");
	}

	@Test
	void spamTrashAndDraftsAreNeverKept() {
		gmailHas("s1", "s2", "s3");
		this.routes.put("/messages/s1", new GoogleApi.Response(200, message("s1", "[\"SPAM\"]", this.contactEmail, this.repEmail, "Spam", "b1@x")));
		this.routes.put("/messages/s2", new GoogleApi.Response(200, message("s2", "[\"TRASH\"]", this.contactEmail, this.repEmail, "Trash", "b2@x")));
		this.routes.put("/messages/s3", new GoogleApi.Response(200, message("s3", "[\"DRAFT\"]", this.repEmail, this.contactEmail, "Draft", "b3@x")));

		run();

		assertThat(stored()).isZero();
	}

	@Test
	void aSecondRunDoesNotDoubleTheMessagesAndFollowsTheHistoryMarker() {
		gmailHas("m1");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Hello", "c1@x")));
		run();
		assertThat(stored()).isEqualTo(1);
		assertThat(this.jdbc.sql("select history_id from mail_sync_state where user_id = :user").param("user", this.rep.getId()).query(String.class).single()).isEqualTo("100");

		// Live mode: Gmail says message m1 was added again (and a new one, m4).
		this.routes.put("/history?", new GoogleApi.Response(200,
				"{\"historyId\":\"150\",\"history\":[{\"id\":\"120\",\"messagesAdded\":[{\"message\":{\"id\":\"m1\"}},{\"message\":{\"id\":\"m4\"}}]}]}"));
		this.routes.put("/messages/m4", new GoogleApi.Response(200, message("m4", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Follow-up", "c4@x")));
		run();

		assertThat(stored()).isEqualTo(2);
		assertThat(this.jdbc.sql("select history_id from mail_sync_state where user_id = :user").param("user", this.rep.getId()).query(String.class).single()).isEqualTo("150");
	}

	@Test
	void anExpiredHistoryMarkerStartsTheLast30DaysAgain() {
		gmailHas("m1");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Hello", "d1@x")));
		run();

		this.routes.put("/history?", new GoogleApi.Response(404, "{}"));
		run();

		var state = this.jdbc.sql("select history_id, backfilling from mail_sync_state where user_id = :user").param("user", this.rep.getId())
			.query((rs, i) -> new Object[] { rs.getString(1), rs.getBoolean(2) }).single();
		assertThat(state[0]).isNull();
		assertThat(state[1]).isEqualTo(true);
		assertThat(stored()).isEqualTo(1);
	}

	@Test
	void aLongBackfillContinuesOnTheNextRun() {
		this.routes.put("/profile", new GoogleApi.Response(200, "{\"historyId\":\"100\"}"));
		this.routes.put("pageToken=PAGE2", new GoogleApi.Response(200, "{\"messages\":[{\"id\":\"m2\"}]}"));
		this.routes.put("/messages?", new GoogleApi.Response(200, "{\"messages\":[{\"id\":\"m1\"}],\"nextPageToken\":\"PAGE2\"}"));
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "First", "e1@x")));
		this.routes.put("/messages/m2", new GoogleApi.Response(200, message("m2", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Second", "e2@x")));

		run();
		assertThat(stored()).isEqualTo(1);
		assertThat(this.jdbc.sql("select backfilling from mail_sync_state where user_id = :user").param("user", this.rep.getId()).query(Boolean.class).single()).isTrue();

		run();
		assertThat(stored()).isEqualTo(2);
		assertThat(this.jdbc.sql("select backfilling from mail_sync_state where user_id = :user").param("user", this.rep.getId()).query(Boolean.class).single()).isFalse();
	}

	@Test
	void aRateLimitFromGoogleKeepsTheWorkAndShowsAMessage() {
		this.routes.put("/profile", new GoogleApi.Response(429, "{}"));

		run();

		assertThat(this.jdbc.sql("select last_error from mail_sync_state where user_id = :user").param("user", this.rep.getId()).query(String.class).single())
			.contains("slow down");
		assertThat(stored()).isZero();
	}

	@Test
	void aLeadsMailIsKeptToo() {
		String leadEmail = "lead-" + UUID.randomUUID() + "@prospect.example";
		String leadId = field(post(this.session, "/api/v1/leads", "{\"firstName\":\"Lena\",\"lastName\":\"Prospect\",\"email\":\"" + leadEmail + "\",\"source\":\"MANUAL\"}"), "id");
		gmailHas("l1");
		this.routes.put("/messages/l1", new GoogleApi.Response(200, message("l1", "[\"INBOX\"]", leadEmail, this.repEmail, "Interested", "f1@x")));

		run();

		assertThat(json(get(this.session, "/api/v1/emails?leadId=" + leadId))).contains("Interested");
	}

	@Test
	void aDealShowsTheMailOfItsMainContact() {
		String company = field(post(this.session, "/api/v1/companies", "{\"name\":\"Deal Co " + UUID.randomUUID() + "\"}"), "id");
		String deal = field(post(this.session, "/api/v1/deals", "{\"name\":\"Big deal\",\"companyId\":\"" + company + "\",\"primaryContactId\":\"" + this.contactId
				+ "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}"), "id");
		gmailHas("g1");
		this.routes.put("/messages/g1", new GoogleApi.Response(200, message("g1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "About the deal", "g1@x")));

		run();

		assertThat(json(get(this.session, "/api/v1/emails?dealId=" + deal))).contains("About the deal");
	}

	@Test
	void aColleagueWhoCanOpenTheSameContactNeverSeesMyMail() {
		gmailHas("m1");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Just between us", "k1@x")));
		run();
		assertThat(json(get(this.session, "/api/v1/emails?contactId=" + this.contactId))).contains("Just between us");

		// An admin can open every contact, but the emails belong to the mailbox they were read from.
		Session admin = signedIn(Role.ADMIN);
		assertThat(get(admin, "/api/v1/contacts/" + this.contactId)).hasStatusOk();
		assertThat(json(get(admin, "/api/v1/emails?contactId=" + this.contactId))).isEqualTo("[]");
	}

	@Test
	void mailWithSomeoneElsesContactIsNotKeptAtAll() {
		Session otherRep = signedIn(Role.REP);
		String othersEmail = "other-" + UUID.randomUUID() + "@client.example";
		post(otherRep, "/api/v1/contacts", "{\"firstName\":\"Omar\",\"lastName\":\"Khan\",\"email\":\"" + othersEmail + "\",\"companyName\":\"Other Co " + UUID.randomUUID() + "\"}");
		gmailHas("x1");
		this.routes.put("/messages/x1", new GoogleApi.Response(200, message("x1", "[\"INBOX\"]", othersEmail, this.repEmail, "Not mine to keep", "l1@x")));

		run();

		assertThat(stored()).isZero();
	}

	@Test
	void anAdminsMailIsMatchedAgainstEveryContact() {
		String adminEmail = uniqueEmail();
		AppUser admin = invite(adminEmail, Role.ADMIN);
		Session adminSession = signIn(uniqueGoogleId(), adminEmail);
		connectMailbox(admin, adminEmail);
		gmailHas("a1");
		this.routes.put("/messages/a1", new GoogleApi.Response(200, message("a1", "[\"INBOX\"]", this.contactEmail, adminEmail, "Seen by the admin", "m1@x")));

		run();

		assertThat(json(get(adminSession, "/api/v1/emails?contactId=" + this.contactId))).contains("Seen by the admin");
	}

	@Test
	void aRepWhoCannotOpenTheContactCannotReadItsEmail() {
		gmailHas("m1");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Private", "h1@x")));
		run();
		Session stranger = signedIn(Role.REP);

		assertThat(get(stranger, "/api/v1/emails?contactId=" + this.contactId)).hasStatus(404);
	}

	@Test
	void exactlyOneRecordMustBeNamed() {
		assertThat(get(this.session, "/api/v1/emails")).hasStatus(400);
		assertThat(get(this.session, "/api/v1/emails?contactId=" + this.contactId + "&leadId=" + UUID.randomUUID())).hasStatus(400);
	}

	@Test
	void turningSyncOffCanAlsoDeleteWhatWasImportedAndCancelsTheGoogleGrantWhenNothingElseUsesIt() {
		gmailHas("m1");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Hello", "i1@x")));
		run();
		assertThat(stored()).isEqualTo(1);
		doReturn(true).when(this.google).revoke("saved-refresh-token");

		MvcTestResult result = withCsrf(this.mvc.delete().uri("/api/v1/inbox?deleteImported=true")).cookie(this.session.accessToken()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(json(result)).contains("\"revokedAtGoogle\":true").contains("\"otherFeaturesStillOn\":false");
		assertThat(stored()).isZero();
		assertThat(this.jdbc.sql("select count(*) from mail_sync_state where user_id = :user").param("user", this.rep.getId()).query(Long.class).single()).isZero();
		// Nothing else used the saved token, so it is gone and Google was told to cancel it.
		assertThat(this.jdbc.sql("select count(*) from mail_connection where user_id = :user").param("user", this.rep.getId()).query(Long.class).single()).isZero();
		verify(this.google).revoke("saved-refresh-token");
	}

	@Test
	void turningSyncOffKeepsTheGoogleGrantWhileMailSendingStillUsesIt() {
		this.jdbc.sql("update mail_connection set scopes = :scopes where user_id = :user")
			.param("scopes", GoogleApi.GMAIL_READ_SCOPE + " " + GoogleApi.GMAIL_SEND_SCOPE).param("user", this.rep.getId()).update();

		MvcTestResult result = withCsrf(this.mvc.delete().uri("/api/v1/inbox")).cookie(this.session.accessToken()).exchange();

		assertThat(json(result)).contains("\"revokedAtGoogle\":false").contains("\"otherFeaturesStillOn\":true");
		verify(this.google, never()).revoke(anyString());
		assertThat(this.jdbc.sql("select scopes from mail_connection where user_id = :user").param("user", this.rep.getId()).query(String.class).single())
			.isEqualTo(GoogleApi.GMAIL_SEND_SCOPE);
	}

	@Test
	void ifGoogleCannotBeReachedTheSavedTokenIsStillDeletedAndTheUserIsTold() {
		doReturn(false).when(this.google).revoke(anyString());

		MvcTestResult result = withCsrf(this.mvc.delete().uri("/api/v1/inbox")).cookie(this.session.accessToken()).exchange();

		assertThat(json(result)).contains("\"revokedAtGoogle\":false").contains("\"otherFeaturesStillOn\":false");
		assertThat(this.jdbc.sql("select count(*) from mail_connection where user_id = :user").param("user", this.rep.getId()).query(Long.class).single()).isZero();
	}

	@Test
	void purgingAContactRemovesItsEmails() {
		gmailHas("m1");
		this.routes.put("/messages/m1", new GoogleApi.Response(200, message("m1", "[\"INBOX\"]", this.contactEmail, this.repEmail, "Hello", "j1@x")));
		run();
		assertThat(withCsrf(this.mvc.delete().uri("/api/v1/contacts/" + this.contactId)).cookie(this.session.accessToken()).exchange()).hasStatus(204);
		this.jdbc.sql("update contact set deleted_at = now() - interval '40 days' where id = cast(:id as uuid)").param("id", this.contactId).update();

		purgeTrash();

		assertThat(stored()).isZero();
	}

	@Autowired
	private com.nexus.crm.trash.TrashService trash;

	private void purgeTrash() {
		this.trash.purgeExpired();
	}

}
