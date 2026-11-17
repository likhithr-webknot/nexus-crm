package com.nexus.crm.notifications;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.identity.team.TeamService;

/**
 * Tells people about work that is slipping: open deals with no activity for a while, and follow-ups that are long overdue.
 *
 * <p>One notification per person per run ("4 of your deals..."), not one per record, so the first run on a busy
 * system does not flood anyone. Each row is marked as alerted in the same transaction as the notifications. Rows are read with
 * plain SQL because this package may read deals and follow-ups but they must not depend on it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

	/** The most rows one run marks. The rest are picked up by the next run. */
	private static final int BATCH = 500;

	/** Held for the length of a run so that two app instances never alert the same rows. */
	private static final long LOCK_KEY = 7_201_126_001L;

	public record Result(int staleDeals, int overdueFollowUps) {
	}

	private final JdbcClient jdbc;

	private final NotificationService notifications;

	private final TeamService teams;

	@Value("${nexus.alerts.enabled:true}")
	private boolean enabled;

	@Value("${nexus.alerts.stale-deal-days:14}")
	private int staleDays;

	@Value("${nexus.alerts.overdue-follow-up-hours:24}")
	private int overdueHours;

	@Scheduled(fixedDelayString = "${nexus.alerts.interval:PT1H}", initialDelayString = "PT2M")
	void scheduled() {
		if (this.enabled) {
			run(Instant.now());
		}
	}

	@Transactional
	public Result run(Instant now) {
		Boolean locked = this.jdbc.sql("select pg_try_advisory_xact_lock(:key)").param("key", LOCK_KEY).query(Boolean.class).single();
		if (!Boolean.TRUE.equals(locked)) {
			return new Result(0, 0);
		}
		Result result = new Result(alertStaleDeals(now), alertOverdueFollowUps(now));
		if (result.staleDeals() + result.overdueFollowUps() > 0) {
			log.info("Alerts sent: staleDeals={} overdueFollowUps={}", result.staleDeals(), result.overdueFollowUps());
		}
		return result;
	}

	private int alertStaleDeals(Instant now) {
		OffsetDateTime cutoff = at(now.minus(Duration.ofDays(this.staleDays)));
		// The last activity is the newest of: the deal's creation, a logged activity, a stage move. The deal's own
		// updated_at is not used because any edit changes it.
		record Stale(UUID id, UUID owner) {
		}
		List<Stale> stale = this.jdbc.sql("""
				with ages as (
				  select d.id, d.owner_id, d.stale_alerted_at,
				         greatest(d.created_at,
				                  coalesce((select max(a.occurred_at) from activity a where a.deal_id = d.id and a.deleted_at is null), d.created_at),
				                  coalesce((select max(h.changed_at) from deal_stage_history h where h.deal_id = d.id), d.created_at)) as last_at
				  from deal d
				  where d.deleted_at is null and d.closed_at is null)
				update deal set stale_alerted_at = :now
				where id in (select id from ages
				             where last_at < :cutoff and (stale_alerted_at is null or stale_alerted_at < last_at)
				             order by id limit :batch)
				returning id, owner_id
				""")
			.param("now", at(now)).param("cutoff", cutoff).param("batch", BATCH)
			.query((rs, i) -> new Stale(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class)))
			.list();

		Map<UUID, Integer> perOwner = new HashMap<>();
		stale.forEach((s) -> perOwner.merge(s.owner(), 1, Integer::sum));
		Map<UUID, Integer> perLeader = new HashMap<>();
		perOwner.forEach((owner, count) -> {
			this.notifications.send(owner, "DEAL_STALE", plural(count, "of your deals has", "of your deals have") + " had no activity for "
					+ this.staleDays + "+ days", "/deals");
			this.teams.leadersOf(owner).stream().filter((leader) -> !leader.equals(owner)).forEach((leader) -> perLeader.merge(leader, count, Integer::sum));
		});
		perLeader.forEach((leader, count) -> this.notifications.send(leader, "DEAL_STALE",
				plural(count, "deal on your team has", "deals on your team have") + " had no activity for " + this.staleDays + "+ days", "/deals"));
		return stale.size();
	}

	private int alertOverdueFollowUps(Instant now) {
		OffsetDateTime cutoff = at(now.minus(Duration.ofHours(this.overdueHours)));
		// A follow-up alerts again only if its due date was moved to a later time and that time passed too.
		List<UUID> assignees = this.jdbc.sql("""
				update follow_up set overdue_alerted_at = :now
				where id in (select id from follow_up
				             where deleted_at is null and status = 'OPEN' and due_at < :cutoff
				               and (overdue_alerted_at is null or overdue_alerted_at < due_at)
				             order by id limit :batch)
				returning assignee_id
				""")
			.param("now", at(now)).param("cutoff", cutoff).param("batch", BATCH)
			.query(UUID.class)
			.list();

		Map<UUID, Integer> perAssignee = new HashMap<>();
		assignees.forEach((a) -> perAssignee.merge(a, 1, Integer::sum));
		perAssignee.forEach((assignee, count) -> this.notifications.send(assignee, "FOLLOW_UP_OVERDUE",
				plural(count, "follow-up is", "follow-ups are") + " overdue", "/calendar"));
		return assignees.size();
	}

	private static String plural(int count, String one, String many) {
		return count + " " + (count == 1 ? one : many);
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
