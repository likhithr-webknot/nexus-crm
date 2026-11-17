package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Sending the same create request twice (double click, retry) makes one deal, not two. */
class IdempotentDealCreationTests extends IntegrationTest {

	private Session rep;

	private String companyId;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		this.rep = signedIn(Role.REP);
		this.companyId = idOf(post(this.rep, "/api/v1/companies", null, "{\"name\":\"Key Co " + UUID.randomUUID() + "\"}"));
	}

	private MvcTestResult post(Session session, String uri, String key, String body) {
		var request = withCsrf(this.mvc.post().uri(uri)).cookie(session.accessToken()).contentType(MediaType.APPLICATION_JSON).content(body);
		return (key == null) ? request.exchange() : request.header("Idempotency-Key", key).exchange();
	}

	private String dealBody(String name) {
		return "{\"name\":\"" + name + "\",\"companyId\":\"" + this.companyId + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}";
	}

	private static String text(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private static String idOf(MvcTestResult result) {
		return text(result).replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
	}

	private long dealsNamed(String name) {
		return this.jdbc.sql("select count(*) from deal where name = :name").param("name", name).query(Long.class).single();
	}

	@Test
	void theSameKeyAndRequestGivesBackTheSameDeal() {
		String name = "Once " + UUID.randomUUID();
		String key = UUID.randomUUID().toString();

		MvcTestResult first = post(this.rep, "/api/v1/deals", key, dealBody(name));
		MvcTestResult second = post(this.rep, "/api/v1/deals", key, dealBody(name));

		assertThat(first).hasStatus(201);
		assertThat(second).hasStatus(201);
		assertThat(idOf(second)).isEqualTo(idOf(first));
		assertThat(text(second)).contains("\"name\":\"" + name + "\"").contains("\"currency\":\"INR\"");
		assertThat(dealsNamed(name)).isEqualTo(1);
	}

	@Test
	void withoutAKeyEveryRequestMakesADeal() {
		String name = "Twice " + UUID.randomUUID();

		post(this.rep, "/api/v1/deals", null, dealBody(name));
		post(this.rep, "/api/v1/deals", null, dealBody(name));

		assertThat(dealsNamed(name)).isEqualTo(2);
	}

	@Test
	void aDifferentKeyMakesAnotherDeal() {
		String name = "Two keys " + UUID.randomUUID();

		post(this.rep, "/api/v1/deals", UUID.randomUUID().toString(), dealBody(name));
		post(this.rep, "/api/v1/deals", UUID.randomUUID().toString(), dealBody(name));

		assertThat(dealsNamed(name)).isEqualTo(2);
	}

	@Test
	void reusingAKeyForADifferentRequestIsRefusedNotAnswerWithTheWrongDeal() {
		String key = UUID.randomUUID().toString();
		post(this.rep, "/api/v1/deals", key, dealBody("First " + UUID.randomUUID()));

		MvcTestResult other = post(this.rep, "/api/v1/deals", key, dealBody("Second " + UUID.randomUUID()));

		assertThat(other).hasStatus(422);
		assertThat(text(other)).contains("already sent with different details");
	}

	@Test
	void aFailedAttemptDoesNotUseUpTheKey() {
		String key = UUID.randomUUID().toString();
		String name = "After failure " + UUID.randomUUID();
		String missingCompany = "{\"name\":\"" + name + "\",\"companyId\":\"" + UUID.randomUUID() + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}";
		assertThat(post(this.rep, "/api/v1/deals", key, missingCompany)).hasStatus(404);

		// The user fixes the form and tries again with the same key. The failed attempt left nothing behind.
		MvcTestResult retry = post(this.rep, "/api/v1/deals", key, dealBody(name));

		assertThat(retry).hasStatus(201);
		assertThat(dealsNamed(name)).isEqualTo(1);
	}

	@Test
	void theSameKeyFromTwoPeopleIsTwoSeparateRequests() {
		Session other = signedIn(Role.REP);
		String otherCompany = idOf(post(other, "/api/v1/companies", null, "{\"name\":\"Other Key Co " + UUID.randomUUID() + "\"}"));
		String key = UUID.randomUUID().toString();
		String name = "Shared key " + UUID.randomUUID();

		MvcTestResult mine = post(this.rep, "/api/v1/deals", key, dealBody(name));
		MvcTestResult theirs = post(other, "/api/v1/deals", key,
				"{\"name\":\"" + name + "\",\"companyId\":\"" + otherCompany + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}");

		assertThat(mine).hasStatus(201);
		assertThat(theirs).hasStatus(201);
		assertThat(idOf(theirs)).isNotEqualTo(idOf(mine));
	}

	@Test
	void twoRequestsAtTheSameMomentMakeOneDeal() throws Exception {
		String name = "Racing " + UUID.randomUUID();
		String key = UUID.randomUUID().toString();
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<Callable<MvcTestResult>> calls = java.util.stream.IntStream.range(0, 6)
				.<Callable<MvcTestResult>>mapToObj((i) -> () -> post(this.rep, "/api/v1/deals", key, dealBody(name)))
				.toList();
			List<String> ids = new java.util.ArrayList<>();
			for (Future<MvcTestResult> done : pool.invokeAll(calls)) {
				MvcTestResult result = done.get();
				assertThat(result).hasStatus(201);
				ids.add(idOf(result));
			}
			assertThat(ids).allMatch((id) -> id.equals(ids.getFirst()));
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(dealsNamed(name)).isEqualTo(1);
	}

	@Test
	void aKeyThatIsTooLongIsRefused() {
		assertThat(post(this.rep, "/api/v1/deals", "k".repeat(101), dealBody("Long key " + UUID.randomUUID()))).hasStatus(422);
	}

	@Test
	void theKeyAndItsAnswerAreSavedWithoutTheRequestText() {
		String key = UUID.randomUUID().toString();
		post(this.rep, "/api/v1/deals", key, dealBody("Stored " + UUID.randomUUID()));

		String hash = this.jdbc.sql("select request_hash from idempotency_key where key = :key").param("key", key).query(String.class).single();

		// Only a fingerprint is stored to compare against, not what was asked.
		assertThat(hash).hasSize(64).matches("[0-9a-f]+");
	}

}
