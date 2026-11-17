package com.nexus.crm.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.mail.GoogleApi;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Pushing follow-ups to Google Calendar. Google itself is replaced by a stub that records what was asked of it. */
class CalendarSyncTests extends IntegrationTest {

	private static final String EVENTS = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

	@MockitoBean
	private GoogleApi google;

	@Autowired
	private CalendarSyncService sync;

	@Autowired
	private JdbcClient jdbc;

	private AppUser rep;

	private Session session;

	/** Each call to the sync claims users that were not run in the last 50 seconds, so the tests move the clock forward. */
	private Instant clock = Instant.now();

	@BeforeEach
	void connectARep() {
		this.jdbc.sql("delete from calendar_link").update();
		this.jdbc.sql("delete from calendar_google_sync").update();
		reset(this.google);
		String email = uniqueEmail();
		this.rep = invite(email, Role.REP);
		this.session = signIn(uniqueGoogleId(), email);
		this.jdbc.sql("insert into calendar_google_sync (user_id) values (:user)").param("user", this.rep.getId()).update();
		when(this.google.configured()).thenReturn(true);
		when(this.google.accessToken(eq(this.rep.getId()), anyString())).thenReturn("token");
		when(this.google.call(anyString(), anyString(), anyString(), any())).thenReturn(new GoogleApi.Response(200, "{}"));
	}

	private int runSync() {
		this.clock = this.clock.plus(Duration.ofMinutes(2));
		return this.sync.syncDue(this.clock);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, String body) {
		return withCsrf(request).cookie(this.session.accessToken()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String field(MvcTestResult result, String name) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
			.replaceAll("^.*?\"" + name + "\":\"([^\"]+)\".*$", "$1");
	}

	private String newFollowUp(String title) {
		String company = field(send(this.mvc.post().uri("/api/v1/companies"), "{\"name\":\"Cal Co " + UUID.randomUUID() + "\"}"), "id");
		String deal = field(send(this.mvc.post().uri("/api/v1/deals"),
				"{\"name\":\"Cal deal\",\"companyId\":\"" + company + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}"), "id");
		String id = field(send(this.mvc.post().uri("/api/v1/follow-ups"),
				"{\"title\":\"" + title + "\",\"dueAt\":\"" + Instant.now().plus(Duration.ofDays(2)) + "\",\"dealId\":\"" + deal + "\"}"), "id");
		dropAutomaticFollowUps();
		return id;
	}

	/** Creating a deal also creates a "Follow up: ..." follow-up for its owner. These tests count events, so remove it. */
	private void dropAutomaticFollowUps() {
		this.jdbc.sql("update follow_up set deleted_at = now() where assignee_id = :user and title like 'Follow up: %' and deleted_at is null")
			.param("user", this.rep.getId()).update();
	}

	private String eventId(String followUpId) {
		return "nc" + followUpId.replace("-", "");
	}

	private long links() {
		return this.jdbc.sql("select count(*) from calendar_link where user_id = :user").param("user", this.rep.getId()).query(Long.class).single();
	}

	private void touch(String followUpId) {
		this.jdbc.sql("update follow_up set title = title || '!', version = version + 1 where id = cast(:id as uuid)").param("id", followUpId).update();
	}

	@Test
	void aNewFollowUpIsPushedOnceWithAStableEventId() {
		String id = newFollowUp("Call Priya");

		runSync();

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(this.google, times(1)).call(eq("POST"), eq(EVENTS), eq("token"), body.capture());
		assertThat(body.getValue()).contains("\"id\":\"" + eventId(id) + "\"").contains("\"summary\":\"Call Priya\"")
			.contains("\"timeZone\":\"UTC\"").contains("\"nexusFollowUp\":\"" + id + "\"");
		assertThat(links()).isEqualTo(1);

		// Nothing changed, so the next run writes nothing.
		runSync();
		verify(this.google, times(1)).call(eq("POST"), anyString(), anyString(), any());
		verify(this.google, never()).call(eq("PATCH"), anyString(), anyString(), any());
	}

	@Test
	void anEditedFollowUpUpdatesItsEvent() {
		String id = newFollowUp("Call Priya");
		runSync();

		touch(id);
		runSync();

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(this.google).call(eq("PATCH"), eq(EVENTS + "/" + eventId(id)), eq("token"), body.capture());
		assertThat(body.getValue()).contains("Call Priya!");
		verify(this.google, times(1)).call(eq("POST"), anyString(), anyString(), any());
	}

	@Test
	void aDoneFollowUpIsMarkedWithATick() {
		String id = newFollowUp("Send quote");
		runSync();

		this.jdbc.sql("update follow_up set status = 'DONE', completed_at = now(), version = version + 1 where id = cast(:id as uuid)").param("id", id).update();
		runSync();

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(this.google).call(eq("PATCH"), anyString(), anyString(), body.capture());
		assertThat(body.getValue()).contains("✓ Send quote");
	}

	@Test
	void aDeletedFollowUpRemovesItsEvent() {
		String id = newFollowUp("Call Priya");
		runSync();

		this.jdbc.sql("update follow_up set deleted_at = now() where id = cast(:id as uuid)").param("id", id).update();
		runSync();

		verify(this.google).call(eq("DELETE"), eq(EVENTS + "/" + eventId(id)), eq("token"), eq(null));
		assertThat(links()).isZero();
	}

	@Test
	void aCancelledFollowUpRemovesItsEvent() {
		String id = newFollowUp("Call Priya");
		runSync();

		this.jdbc.sql("update follow_up set status = 'CANCELLED', version = version + 1 where id = cast(:id as uuid)").param("id", id).update();
		runSync();

		verify(this.google).call(eq("DELETE"), eq(EVENTS + "/" + eventId(id)), eq("token"), eq(null));
		assertThat(links()).isZero();
	}

	@Test
	void anInsertThatFindsTheEventAlreadyThereUpdatesItInstead() {
		String id = newFollowUp("Call Priya");
		when(this.google.call(eq("POST"), anyString(), anyString(), any())).thenReturn(new GoogleApi.Response(409, "{}"));

		runSync();

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(this.google).call(eq("PATCH"), eq(EVENTS + "/" + eventId(id)), eq("token"), body.capture());
		assertThat(body.getValue()).contains("\"status\":\"confirmed\"");
		assertThat(links()).isEqualTo(1);
	}

	@Test
	void aRateLimitStopsTheRunKeepsTheWorkForLaterAndShowsAMessage() {
		String id = newFollowUp("Call Priya");
		when(this.google.call(eq("POST"), anyString(), anyString(), any())).thenReturn(new GoogleApi.Response(429, "{}"));

		runSync();

		assertThat(links()).isZero();
		assertThat(this.jdbc.sql("select last_error from calendar_google_sync where user_id = :user").param("user", this.rep.getId())
			.query(String.class).single()).contains("slow down");

		// Google recovers: the same follow-up is pushed on the next run, still with the same event id.
		when(this.google.call(eq("POST"), anyString(), anyString(), any())).thenReturn(new GoogleApi.Response(200, "{}"));
		runSync();
		assertThat(links()).isEqualTo(1);
		assertThat(this.jdbc.sql("select google_event_id from calendar_link where user_id = :user").param("user", this.rep.getId())
			.query(String.class).single()).isEqualTo(eventId(id));
	}

	@Test
	void anEventTheUserDeletedInGoogleStaysDeleted() {
		String id = newFollowUp("Call Priya");
		runSync();

		when(this.google.call(eq("PATCH"), anyString(), anyString(), any())).thenReturn(new GoogleApi.Response(404, "{}"));
		touch(id);
		runSync();
		// The follow-up was seen as synced, so a further run does not try again or create the event anew.
		runSync();

		verify(this.google, times(1)).call(eq("POST"), anyString(), anyString(), any());
		verify(this.google, times(1)).call(eq("PATCH"), anyString(), anyString(), any());
	}

	@Test
	void aRevokedConnectionIsReportedAndNothingIsSent() {
		newFollowUp("Call Priya");
		when(this.google.accessToken(eq(this.rep.getId()), anyString())).thenThrow(new GoogleApi.Revoked());

		runSync();

		verify(this.google, never()).call(anyString(), anyString(), anyString(), any());
		assertThat(this.jdbc.sql("select last_error from calendar_google_sync where user_id = :user").param("user", this.rep.getId())
			.query(String.class).single()).contains("connected again");
	}

	@Test
	void aFollowUpFarOutsideTheWindowIsNotPushed() {
		String id = newFollowUp("Next year");
		this.jdbc.sql("update follow_up set due_at = now() + interval '200 days' where id = cast(:id as uuid)").param("id", id).update();

		runSync();

		verify(this.google, never()).call(eq("POST"), anyString(), anyString(), any());
	}

	@Test
	void anotherUsersFollowUpIsNeverPushedToThisCalendar() {
		// A second rep has no sync, so only this rep's calendar is written.
		Session other = signedIn(Role.REP);
		String company = field(withCsrfPost("/api/v1/companies", other, "{\"name\":\"Other Co " + UUID.randomUUID() + "\"}"), "id");
		String deal = field(withCsrfPost("/api/v1/deals", other,
				"{\"name\":\"Other deal\",\"companyId\":\"" + company + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}"), "id");
		withCsrfPost("/api/v1/follow-ups", other, "{\"title\":\"Not mine\",\"dueAt\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\",\"dealId\":\"" + deal + "\"}");

		runSync();

		verify(this.google, never()).call(anyString(), anyString(), anyString(), any());
	}

	private MvcTestResult withCsrfPost(String uri, Session who, String body) {
		return withCsrf(this.mvc.post().uri(uri)).cookie(who.accessToken()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

}
