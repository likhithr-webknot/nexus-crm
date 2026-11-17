package com.nexus.crm.trash;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Records stay in the recycle bin for 30 days, then the nightly clean-up removes them for good. */
class TrashPurgeTests extends IntegrationTest {

	@Autowired
	private TrashService trash;

	@Autowired
	private JdbcClient jdbc;

	private Session admin;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	private MvcTestResult send(MockMvcRequestBuilder request, String body) {
		var builder = withCsrf(request).cookie(this.admin.accessToken());
		return (body == null) ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String id(MvcTestResult result) {
		String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		return json.replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
	}

	private String newLead() {
		return id(send(this.mvc.post().uri("/api/v1/leads"),
				"{\"firstName\":\"Purge\",\"lastName\":\"Test\",\"email\":\"" + uniqueEmail() + "\",\"source\":\"MANUAL\"}"));
	}

	private void deletedDaysAgo(String table, String id, int days) {
		this.jdbc.sql("update " + table + " set deleted_at = now() - make_interval(days => :days) where id = :id")
			.param("days", days).param("id", UUID.fromString(id)).update();
	}

	private boolean exists(String table, String id) {
		return this.jdbc.sql("select count(*) from " + table + " where id = :id").param("id", UUID.fromString(id)).query(Long.class).single() == 1;
	}

	private Instant cutoff() {
		return Instant.now().minus(TrashService.RETENTION_DAYS, ChronoUnit.DAYS);
	}

	@Test
	void aRecordOlderThan30DaysIsRemovedAndAYoungerOneStays() {
		String old = newLead();
		String young = newLead();
		send(this.mvc.delete().uri("/api/v1/leads/" + old), null);
		send(this.mvc.delete().uri("/api/v1/leads/" + young), null);
		deletedDaysAgo("lead", old, 31);
		deletedDaysAgo("lead", young, 29);

		this.trash.purgeDeletedBefore(cutoff());

		assertThat(exists("lead", old)).isFalse();
		assertThat(exists("lead", young)).isTrue();
	}

	@Test
	void aLiveRecordIsNeverRemoved() {
		String live = newLead();

		this.trash.purgeDeletedBefore(Instant.now().plus(1, ChronoUnit.DAYS));

		assertThat(exists("lead", live)).isTrue();
	}

	@Test
	void aDealAndItsCompanyGoTogetherAndTheirHistoryGoesToo() {
		String companyId = id(send(this.mvc.post().uri("/api/v1/companies"), "{\"name\":\"Purge Co " + UUID.randomUUID() + "\"}"));
		String dealId = id(send(this.mvc.post().uri("/api/v1/deals"),
				"{\"name\":\"Purge deal\",\"companyId\":\"" + companyId + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}"));
		send(this.mvc.delete().uri("/api/v1/deals/" + dealId), null);
		send(this.mvc.delete().uri("/api/v1/companies/" + companyId), null);
		deletedDaysAgo("deal", dealId, 40);
		deletedDaysAgo("company", companyId, 40);

		this.trash.purgeDeletedBefore(cutoff());

		assertThat(exists("deal", dealId)).isFalse();
		assertThat(exists("company", companyId)).isFalse();
		assertThat(this.jdbc.sql("select count(*) from deal_stage_history where deal_id = :id").param("id", UUID.fromString(dealId))
			.query(Long.class).single()).isZero();
	}

	@Test
	void aCompanyThatStillHasAYoungDeletedDealStaysUntilTheDealIsGone() {
		String companyId = id(send(this.mvc.post().uri("/api/v1/companies"), "{\"name\":\"Held Co " + UUID.randomUUID() + "\"}"));
		String dealId = id(send(this.mvc.post().uri("/api/v1/deals"),
				"{\"name\":\"Young deal\",\"companyId\":\"" + companyId + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}"));
		send(this.mvc.delete().uri("/api/v1/deals/" + dealId), null);
		send(this.mvc.delete().uri("/api/v1/companies/" + companyId), null);
		deletedDaysAgo("deal", dealId, 5);
		deletedDaysAgo("company", companyId, 40);

		// The company cannot go yet: the young deal still points at it. That must not stop the clean-up or lose data.
		this.trash.purgeDeletedBefore(cutoff());
		assertThat(exists("company", companyId)).isTrue();
		assertThat(exists("deal", dealId)).isTrue();

		deletedDaysAgo("deal", dealId, 40);
		this.trash.purgeDeletedBefore(cutoff());
		assertThat(exists("deal", dealId)).isFalse();
		assertThat(exists("company", companyId)).isFalse();
	}

	@Test
	void theBinReportsHowManyRecordsThereAreInAll() {
		String id = newLead();
		send(this.mvc.delete().uri("/api/v1/leads/" + id), null);

		String json = new String(this.mvc.get().uri("/api/v1/trash?type=LEAD").cookie(this.admin.accessToken()).exchange()
			.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

		assertThat(json).contains("\"total\":").contains("\"retentionDays\":30").contains(id);
	}

}
