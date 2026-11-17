package com.nexus.crm.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The same unread notification is never stored twice for one person. */
class NotificationDedupeTests extends IntegrationTest {

	@Autowired
	private NotificationService notifications;

	@Autowired
	private JdbcClient jdbc;

	private UUID newUser() {
		return invite(uniqueEmail(), Role.REP).getId();
	}

	private long count(UUID user) {
		return this.jdbc.sql("select count(*) from notification where recipient_id = :user").param("user", user).query(Long.class).single();
	}

	@Test
	void sendingTheSameUnreadMessageTwiceKeepsOne() {
		UUID user = newUser();

		this.notifications.send(user, "DEAL_STALE", "3 of your deals have had no activity", "/deals");
		this.notifications.send(user, "DEAL_STALE", "3 of your deals have had no activity", "/deals");

		assertThat(count(user)).isEqualTo(1);
	}

	@Test
	void aDifferentTitleTypeOrLinkIsAnotherNotification() {
		UUID user = newUser();

		this.notifications.send(user, "DEAL_STALE", "Same", "/deals");
		this.notifications.send(user, "DEAL_STALE", "Different title", "/deals");
		this.notifications.send(user, "FOLLOW_UP_OVERDUE", "Same", "/deals");
		this.notifications.send(user, "DEAL_STALE", "Same", "/calendar");

		assertThat(count(user)).isEqualTo(4);
	}

	@Test
	void theSameMessageForTwoPeopleIsStoredForBoth() {
		UUID first = newUser();
		UUID second = newUser();

		this.notifications.send(first, "DEAL_STALE", "Same", "/deals");
		this.notifications.send(second, "DEAL_STALE", "Same", "/deals");

		assertThat(count(first)).isEqualTo(1);
		assertThat(count(second)).isEqualTo(1);
	}

	@Test
	void onceItIsReadTheSameMessageCanBeSentAgain() {
		UUID user = newUser();
		this.notifications.send(user, "DEAL_STALE", "Same", "/deals");
		this.notifications.markRead(user, null);

		this.notifications.send(user, "DEAL_STALE", "Same", "/deals");

		assertThat(count(user)).isEqualTo(2);
	}

	@Test
	void aMessageWithNoLinkIsDeduplicatedToo() {
		UUID user = newUser();

		this.notifications.send(user, "AUTOMATION", "No link here", null);
		this.notifications.send(user, "AUTOMATION", "No link here", null);

		assertThat(count(user)).isEqualTo(1);
	}

	@Test
	void aVeryLongTitleIsStillAccepted() {
		UUID user = newUser();
		String title = "x".repeat(5000);

		this.notifications.send(user, "AUTOMATION", title, "/deals");
		this.notifications.send(user, "AUTOMATION", title, "/deals");

		assertThat(count(user)).isEqualTo(1);
	}

	@Test
	void twoRequestsAtTheSameMomentCannotBothAddIt() throws Exception {
		UUID user = newUser();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Void>> sends = java.util.stream.IntStream.range(0, 8).<Callable<Void>>mapToObj((i) -> () -> {
				this.notifications.send(user, "DEAL_STALE", "Racing", "/deals");
				return null;
			}).toList();
			for (Future<Void> done : pool.invokeAll(sends)) {
				done.get();
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(count(user)).isEqualTo(1);
	}

}
