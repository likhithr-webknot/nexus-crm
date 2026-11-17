package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Giving many leads to one person: one notification, a timeline entry and audit row per lead, and a bounded cost. */
class BulkAssignApiTests extends IntegrationTest {

	private Session admin;

	private AppUser rep;

	private Session repSession;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void setUp() {
		this.admin = signedIn(Role.ADMIN);
		String email = uniqueEmail();
		this.rep = invite(email, Role.REP);
		this.repSession = signIn(uniqueGoogleId(), email);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, Session session, String body) {
		var builder = withCsrf(request).cookie(session.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult get(String uri, Session session) {
		return this.mvc.get().uri(uri).cookie(session.accessToken()).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private List<String> newLeads(int count) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String body = json(send(this.mvc.post().uri("/api/v1/leads"), this.admin,
					"{\"firstName\":\"Bulk\",\"lastName\":\"Lead" + i + "\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"MANUAL\"}"));
			ids.add(body.replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1"));
		}
		return ids;
	}

	private MvcTestResult assign(List<String> ids, String ownerId) {
		String list = ids.stream().map((id) -> "\"" + id + "\"").collect(Collectors.joining(","));
		return send(this.mvc.post().uri("/api/v1/leads/bulk-assign"), this.admin,
				"{\"ids\":[" + list + "],\"ownerId\":" + ((ownerId == null) ? "null" : "\"" + ownerId + "\"") + "}");
	}

	private String notificationsOf(Session session) {
		return json(get("/api/v1/notifications?size=50", session));
	}

	private long statements() {
		return this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
	}

	@Test
	void theNewOwnerGetsOneNotificationForTheWholeBatch() {
		List<String> ids = newLeads(5);

		assertThat(assign(ids, this.rep.getId().toString())).hasStatusOk();

		String notifications = notificationsOf(this.repSession);
		assertThat(notifications).contains("5 leads were assigned to you");
		assertThat(notifications.split("leads were assigned to you", -1)).hasSize(2);
		assertThat(notifications).doesNotContain("A lead was assigned to you");
	}

	@Test
	void everyLeadStillGetsATimelineEntryAndAnAuditRow() {
		List<String> ids = newLeads(3);

		assign(ids, this.rep.getId().toString());

		for (String id : ids) {
			assertThat(json(get("/api/v1/activities?leadId=" + id, this.admin))).contains("Lead assigned");
			assertThat(json(get("/api/v1/audit?entityType=Lead&entityId=" + id, this.admin))).contains("ownerId");
			assertThat(json(get("/api/v1/leads/" + id, this.admin))).contains(this.rep.getId().toString());
		}
	}

	@Test
	void reportsHowManyReallyChangedAndLeavesTheRestAlone() {
		List<String> ids = newLeads(4);
		assign(ids.subList(0, 2), this.rep.getId().toString());

		MvcTestResult result = assign(ids, this.rep.getId().toString());

		assertThat(json(result)).contains("\"count\":2");
	}

	@Test
	void assigningAgainChangesNothingAndNotifiesNobody() {
		List<String> ids = newLeads(3);
		assign(ids, this.rep.getId().toString());
		String before = notificationsOf(this.repSession);

		MvcTestResult again = assign(ids, this.rep.getId().toString());

		assertThat(json(again)).contains("\"count\":0");
		assertThat(notificationsOf(this.repSession)).isEqualTo(before);
	}

	@Test
	void unassigningManyLeadsNotifiesNobody() {
		List<String> ids = newLeads(3);
		assign(ids, this.rep.getId().toString());
		String before = notificationsOf(this.repSession);

		assertThat(assign(ids, null)).hasStatusOk();

		assertThat(json(get("/api/v1/leads/" + ids.getFirst(), this.admin))).doesNotContain(this.rep.getId().toString());
		assertThat(notificationsOf(this.repSession)).isEqualTo(before);
	}

	@Test
	void ifOneLeadIsMissingNothingChanges() {
		List<String> ids = newLeads(2);
		List<String> withGhost = new ArrayList<>(ids);
		withGhost.add(UUID.randomUUID().toString());

		assertThat(assign(withGhost, this.rep.getId().toString())).hasStatus(404);

		assertThat(json(get("/api/v1/leads/" + ids.getFirst(), this.admin))).doesNotContain(this.rep.getId().toString());
		assertThat(notificationsOf(this.repSession)).doesNotContain("leads were assigned");
	}

	@Test
	void theCostGrowsByAFewStatementsPerLeadNotByDozens() {
		List<String> small = newLeads(3);
		List<String> large = newLeads(13);

		long start = statements();
		assign(small, this.rep.getId().toString());
		long smallCost = statements() - start;

		start = statements();
		assign(large, this.rep.getId().toString());
		long largeCost = statements() - start;

		// Ten more leads. Each needs its own update, audit row and timeline entry, and nothing more.
		assertThat((largeCost - smallCost) / 10.0).isLessThanOrEqualTo(4.0);
	}

}
