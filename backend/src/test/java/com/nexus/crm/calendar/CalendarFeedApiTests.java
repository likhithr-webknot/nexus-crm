package com.nexus.crm.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The private calendar link: it works without signing in, shows only the owner's follow-ups, and can be replaced or removed. */
class CalendarFeedApiTests extends IntegrationTest {

	private MvcTestResult send(MockMvcRequestBuilder request, Session session, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private String field(String json, String name) {
		return json.replaceAll("^.*?\"" + name + "\":\"([^\"]+)\".*$", "$1");
	}

	/** The path part of a feed URL, as a calendar app would request it. */
	private String pathOf(String url) {
		return url.replaceFirst("^https?://[^/]+", "");
	}

	private String newFollowUp(Session session, String title) {
		String company = field(json(send(this.mvc.post().uri("/api/v1/companies"), session, "{\"name\":\"Feed Co " + UUID.randomUUID() + "\"}")), "id");
		String deal = field(json(send(this.mvc.post().uri("/api/v1/deals"), session,
				"{\"name\":\"Feed deal\",\"companyId\":\"" + company + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}")), "id");
		return field(json(send(this.mvc.post().uri("/api/v1/follow-ups"), session,
				"{\"title\":\"" + title + "\",\"dueAt\":\"" + Instant.now().plus(Duration.ofDays(2)) + "\",\"dealId\":\"" + deal + "\"}")), "id");
	}

	@Test
	void theLinkWorksWithoutSigningInAndListsTheOwnersFollowUps() {
		Session rep = signedIn(Role.REP);
		String title = "Feed call " + UUID.randomUUID();
		newFollowUp(rep, title);
		String url = field(json(send(this.mvc.post().uri("/api/v1/calendar/feed"), rep, null)), "url");

		MvcTestResult feed = this.mvc.get().uri(pathOf(url)).exchange();

		assertThat(feed).hasStatusOk();
		assertThat(feed.getResponse().getContentType()).startsWith("text/calendar");
		assertThat(feed.getResponse().getHeader("Cache-Control")).contains("no-store");
		assertThat(json(feed)).startsWith("BEGIN:VCALENDAR").contains("SUMMARY:" + title).contains("END:VCALENDAR");
	}

	@Test
	void anotherUsersFollowUpsAreNotInTheFeed() {
		Session rep = signedIn(Role.REP);
		Session other = signedIn(Role.REP);
		String title = "Private " + UUID.randomUUID();
		newFollowUp(other, title);
		String url = field(json(send(this.mvc.post().uri("/api/v1/calendar/feed"), rep, null)), "url");

		assertThat(json(this.mvc.get().uri(pathOf(url)).exchange())).doesNotContain(title);
	}

	@Test
	void replacingTheLinkStopsTheOldOneAndRemovingStopsTheNewOne() {
		Session rep = signedIn(Role.REP);
		String first = pathOf(field(json(send(this.mvc.post().uri("/api/v1/calendar/feed"), rep, null)), "url"));
		String second = pathOf(field(json(send(this.mvc.post().uri("/api/v1/calendar/feed"), rep, null)), "url"));

		assertThat(this.mvc.get().uri(first).exchange()).hasStatus(404);
		assertThat(this.mvc.get().uri(second).exchange()).hasStatusOk();

		assertThat(send(this.mvc.delete().uri("/api/v1/calendar/feed"), rep, null)).hasStatus(204);
		assertThat(this.mvc.get().uri(second).exchange()).hasStatus(404);
	}

	@Test
	void aWrongOrMalformedLinkIsAPlain404() {
		assertThat(this.mvc.get().uri("/api/v1/calendar/ics/" + "A".repeat(43) + ".ics").exchange()).hasStatus(404);
		assertThat(this.mvc.get().uri("/api/v1/calendar/ics/short.ics").exchange()).hasStatus(404);
	}

	@Test
	void managingTheLinkNeedsSignIn() {
		assertThat(this.mvc.get().uri("/api/v1/calendar/feed").exchange()).hasStatus(401);
	}

}
