package com.nexus.crm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.support.IntegrationTest;

/** A user may make 30 AI requests an hour. Requests that arrive at the same moment must not all slip through. */
class AiRateLimitTests extends IntegrationTest {

	@Autowired
	private AiService ai;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void sixtyRequestsAtOnceTakeExactlyThirtySlots() throws Exception {
		AppUser user = invite(uniqueEmail(), Role.REP);
		CurrentUser current = new CurrentUser(user.getId(), Role.REP, java.util.UUID.randomUUID());
		int attempts = 60;
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger accepted = new AtomicInteger();
		AtomicInteger refused = new AtomicInteger();

		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<?>> all = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				all.add(pool.submit(() -> {
					go.await();
					try {
						this.ai.reserve(current, "test");
						accepted.incrementAndGet();
					}
					catch (ApiException limitReached) {
						refused.incrementAndGet();
					}
					return null;
				}));
			}
			go.countDown();
			for (Future<?> future : all) {
				future.get();
			}
		}

		assertThat(accepted.get()).isEqualTo(30);
		assertThat(refused.get()).isEqualTo(30);
		assertThat(this.jdbc.sql("select count(*) from ai_request where user_id = :id").param("id", user.getId()).query(Long.class).single())
			.isEqualTo(30L);
	}

	@Test
	void aReleasedSlotCanBeUsedAgain() {
		AppUser user = invite(uniqueEmail(), Role.REP);
		CurrentUser current = new CurrentUser(user.getId(), Role.REP, java.util.UUID.randomUUID());
		java.util.UUID last = null;
		for (int i = 0; i < 30; i++) {
			last = this.ai.reserve(current, "test");
		}
		assertThatThrownBy(() -> this.ai.reserve(current, "test")).isInstanceOf(ApiException.class);

		this.ai.release(last);

		assertThat(this.ai.reserve(current, "test")).isNotNull();
	}

}
