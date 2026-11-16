package com.nexus.crm.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.CurrentUser;

/** The numbers on the dashboard and the reports page. */
@Service
@RequiredArgsConstructor
public class ReportingService {

	/** How many points each small trend line has. */
	static final int BUCKETS = 7;

	/** A sentinel for "no IDs", because an empty IN () is not valid SQL. */
	private static final UUID NOBODY = new UUID(0, 0);

	public record Kpi(String id, double value, Double change, double[] trend) {
	}

	public record SourceShare(String source, long count, double share) {
	}

	public record StageTotal(String stageCode, String name, long count, double amount, long unconverted) {
	}

	public record Dashboard(List<Kpi> kpis, List<SourceShare> leadSources, List<StageTotal> pipeline) {
	}

	public record MonthRevenue(YearMonth month, double revenue, BigDecimal target) {
	}

	public record FunnelStep(String label, long count) {
	}

	public record SourceRevenue(String source, double revenue) {
	}

	public record TeamRow(UUID userId, String name, long leads, long dealsWon, double revenue, double conversion) {
	}

	public record Overview(List<FunnelStep> funnel, List<SourceRevenue> revenueBySource, List<TeamRow> team,
			double averageDealSize, List<Kpi> kpis) {
	}

	/** Which owners' records a query may see. Null IDs means everyone's. */
	private record Scope(Set<UUID> ids) {

		boolean all() {
			return this.ids == null;
		}

		Collection<UUID> list() {
			return (this.ids == null || this.ids.isEmpty()) ? List.of(NOBODY) : this.ids;
		}

	}

	private final JdbcClient jdbc;

	private final UserService users;

	private final RevenueTargetRepository targets;

	// ---- dashboard ----

	@Transactional(readOnly = true)
	public Dashboard dashboard(CurrentUser user, LocalDate from, LocalDate to) {
		Scope scope = scope(user);
		Instant start = start(from);
		Instant end = start(to.plusDays(1));
		return new Dashboard(kpis(scope, start, end), leadSources(scope, start, end), pipeline(scope));
	}

	/** The seven numbers at the top of the dashboard, each with its change against the period before. */
	private List<Kpi> kpis(Scope scope, Instant from, Instant to) {
		Instant previousFrom = from.minus(java.time.Duration.between(from, to));
		double[] fresh = leadSeries(scope, "l.created_at", "", from, to);
		double[] freshBefore = leadSeries(scope, "l.created_at", "", previousFrom, from);
		double[] converted = leadSeries(scope, "l.converted_at", "", from, to);
		double[] convertedBefore = leadSeries(scope, "l.converted_at", "", previousFrom, from);
		double[] qualified = qualifiedSeries(scope, from, to);
		double[] qualifiedBefore = qualifiedSeries(scope, previousFrom, from);
		double[] won = dealSeries(scope, "WON", "count(*)", from, to);
		double[] wonBefore = dealSeries(scope, "WON", "count(*)", previousFrom, from);
		double[] lost = dealSeries(scope, "LOST", "count(*)", from, to);
		double[] lostBefore = dealSeries(scope, "LOST", "count(*)", previousFrom, from);
		double[] revenue = dealSeries(scope, "WON", "coalesce(sum(d.base_amount), 0)", from, to);
		double[] revenueBefore = dealSeries(scope, "WON", "coalesce(sum(d.base_amount), 0)", previousFrom, from);

		// "Total leads" counts every lead created up to the end of the range, so its trend is a running total.
		long totalBeforeRange = countLeadsCreatedBefore(scope, from);
		long totalBeforePrevious = countLeadsCreatedBefore(scope, previousFrom);
		double[] total = runningTotal(totalBeforeRange, fresh);

		double[] conversion = ratio(converted, fresh);
		double conversionNow = percent(sum(converted), sum(fresh));
		double conversionBefore = percent(sum(convertedBefore), sum(freshBefore));

		List<Kpi> list = new ArrayList<>();
		list.add(new Kpi("total", totalBeforeRange + sum(fresh), change(totalBeforePrevious + sum(freshBefore), totalBeforeRange + sum(fresh)), total));
		list.add(new Kpi("new", sum(fresh), change(sum(freshBefore), sum(fresh)), fresh));
		list.add(new Kpi("qualified", sum(qualified), change(sum(qualifiedBefore), sum(qualified)), qualified));
		list.add(new Kpi("won", sum(won), change(sum(wonBefore), sum(won)), won));
		list.add(new Kpi("lost", sum(lost), change(sum(lostBefore), sum(lost)), lost));
		list.add(new Kpi("revenue", sum(revenue), change(sum(revenueBefore), sum(revenue)), revenue));
		list.add(new Kpi("conversion", conversionNow, change(conversionBefore, conversionNow), conversion));
		return list;
	}

	private List<SourceShare> leadSources(Scope scope, Instant from, Instant to) {
		List<Map.Entry<String, Long>> rows = jdbc.sql("""
				select l.source as source, count(*) as n from lead l
				where l.deleted_at is null and %s and l.created_at >= :from and l.created_at < :to
				group by l.source order by n desc
				""".formatted(LEAD_SCOPE))
			.param("all", scope.all()).param("ids", scope.list())
			.param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query((rs, i) -> Map.entry(rs.getString("source"), rs.getLong("n")))
			.list();
		long total = rows.stream().mapToLong(Map.Entry::getValue).sum();
		return rows.stream()
			.map((r) -> new SourceShare(r.getKey(), r.getValue(), total == 0 ? 0 : Math.round(r.getValue() * 1000.0 / total) / 10.0))
			.toList();
	}

	/** Open deals now, per stage, in INR at the latest exchange rates. A deal with no rate is counted but not summed. */
	private List<StageTotal> pipeline(Scope scope) {
		return jdbc.sql("""
				select s.code, s.name, count(d.id) as n,
				       coalesce(sum(d.amount * r.rate), 0) as amount,
				       count(d.id) filter (where r.rate is null) as unconverted
				from pipeline_stage s
				left join deal d on d.stage_code = s.code and d.deleted_at is null and %s
				left join lateral (
				    select case when c.is_base then 1 else (
				        select e.rate_to_base from exchange_rate e where e.currency = c.code order by e.rate_date desc limit 1)
				    end as rate
				    from currency c where c.code = d.currency
				) r on true
				where s.kind = 'OPEN'
				group by s.code, s.name, s.position order by s.position
				""".formatted(DEAL_SCOPE))
			.param("all", scope.all()).param("ids", scope.list())
			.query((rs, i) -> new StageTotal(rs.getString("code"), rs.getString("name"), rs.getLong("n"),
					rs.getDouble("amount"), rs.getLong("unconverted")))
			.list();
	}

	// ---- revenue against target ----

	/** Won revenue per month for the last {@code months} months, with each month's target. */
	@Transactional(readOnly = true)
	public List<MonthRevenue> revenueByMonth(CurrentUser user, int months) {
		Scope scope = scope(user);
		YearMonth last = YearMonth.now(ZoneOffset.UTC);
		YearMonth first = last.minusMonths(months - 1L);
		Map<YearMonth, Double> revenue = new java.util.HashMap<>();
		jdbc.sql("""
				select date_trunc('month', d.closed_at at time zone 'UTC') as month, coalesce(sum(d.base_amount), 0) as revenue
				from deal d
				where d.deleted_at is null and d.stage_code = 'WON' and %s
				  and d.closed_at >= :from and d.closed_at < :to
				group by 1
				""".formatted(DEAL_SCOPE))
			.param("all", scope.all()).param("ids", scope.list())
			.param("from", Timestamp.from(start(first.atDay(1)))).param("to", Timestamp.from(start(last.plusMonths(1).atDay(1))))
			.query((rs, i) -> {
				revenue.put(YearMonth.from(rs.getTimestamp("month").toInstant().atZone(ZoneOffset.UTC)), rs.getDouble("revenue"));
				return 0;
			})
			.list();
		Map<LocalDate, BigDecimal> targetByMonth = new java.util.HashMap<>();
		if (scope.all()) {
			this.targets.findAllById(java.util.stream.Stream.iterate(first, (m) -> m.plusMonths(1)).limit(months)
				.map((m) -> m.atDay(1)).toList()).forEach((t) -> targetByMonth.put(t.getMonth(), t.getAmount()));
		}
		List<MonthRevenue> result = new ArrayList<>();
		for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
			result.add(new MonthRevenue(m, revenue.getOrDefault(m, 0.0), targetByMonth.get(m.atDay(1))));
		}
		return result;
	}

	@Transactional(readOnly = true)
	public List<RevenueTarget> targetsForYear(int year) {
		return this.targets.findAllById(java.util.stream.IntStream.rangeClosed(1, 12).mapToObj((m) -> LocalDate.of(year, m, 1)).toList());
	}

	@Transactional
	public RevenueTarget setTarget(YearMonth month, BigDecimal amount, UUID adminId) {
		RevenueTarget target = this.targets.findById(month.atDay(1)).orElse(null);
		if (target == null) {
			return this.targets.save(new RevenueTarget(month.atDay(1), amount, adminId));
		}
		target.change(amount, adminId);
		return target;
	}

	// ---- reports page ----

	@Transactional(readOnly = true)
	public Overview overview(CurrentUser user, LocalDate from, LocalDate to) {
		Scope scope = scope(user);
		Instant start = start(from);
		Instant end = start(to.plusDays(1));

		FunnelRow f = jdbc.sql("""
				select count(*) as total,
				  count(*) filter (where exists (select 1 from lead_status_history h where h.lead_id = l.id
				                   and h.to_status in ('CONTACTED','QUALIFIED','CONVERTED'))) as contacted,
				  count(*) filter (where exists (select 1 from lead_status_history h where h.lead_id = l.id
				                   and h.to_status in ('QUALIFIED','CONVERTED'))) as qualified,
				  count(*) filter (where l.converted_deal_id is not null) as converted,
				  count(*) filter (where exists (select 1 from deal d where d.id = l.converted_deal_id
				                   and d.deleted_at is null and d.stage_code = 'WON')) as won
				from lead l
				where l.deleted_at is null and %s and l.created_at >= :from and l.created_at < :to
				""".formatted(LEAD_SCOPE))
			.param("all", scope.all()).param("ids", scope.list())
			.param("from", Timestamp.from(start)).param("to", Timestamp.from(end))
			.query((rs, i) -> new FunnelRow(rs.getLong("total"), rs.getLong("contacted"), rs.getLong("qualified"),
					rs.getLong("converted"), rs.getLong("won")))
			.single();
		List<FunnelStep> funnel = List.of(new FunnelStep("New leads", f.total()), new FunnelStep("Contacted", f.contacted()),
				new FunnelStep("Qualified", f.qualified()), new FunnelStep("Converted", f.converted()),
				new FunnelStep("Won", f.won()));

		List<SourceRevenue> bySource = jdbc.sql("""
				select coalesce(d.source, 'MANUAL') as source, coalesce(sum(d.base_amount), 0) as revenue
				from deal d
				where d.deleted_at is null and d.stage_code = 'WON' and %s and d.closed_at >= :from and d.closed_at < :to
				group by 1 order by revenue desc
				""".formatted(DEAL_SCOPE))
			.param("all", scope.all()).param("ids", scope.list())
			.param("from", Timestamp.from(start)).param("to", Timestamp.from(end))
			.query((rs, i) -> new SourceRevenue(rs.getString("source"), rs.getDouble("revenue")))
			.list();

		List<TeamRow> team = jdbc.sql("""
				select u.id, u.display_name,
				  (select count(*) from lead l where l.owner_id = u.id and l.deleted_at is null
				     and l.created_at >= :from and l.created_at < :to) as leads,
				  (select count(*) from lead l where l.owner_id = u.id and l.deleted_at is null
				     and l.converted_at >= :from and l.converted_at < :to) as converted,
				  (select count(*) from deal d where d.owner_id = u.id and d.deleted_at is null and d.stage_code = 'WON'
				     and d.closed_at >= :from and d.closed_at < :to) as won,
				  (select coalesce(sum(d.base_amount), 0) from deal d where d.owner_id = u.id and d.deleted_at is null
				     and d.stage_code = 'WON' and d.closed_at >= :from and d.closed_at < :to) as revenue
				from app_user u
				where u.status = 'ACTIVE' and (:all or u.id in (:ids))
				order by revenue desc, u.display_name
				""")
			.param("all", scope.all()).param("ids", scope.list())
			.param("from", Timestamp.from(start)).param("to", Timestamp.from(end))
			.query((rs, i) -> new TeamRow(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getLong("leads"),
					rs.getLong("won"), rs.getDouble("revenue"), percent(rs.getLong("converted"), rs.getLong("leads"))))
			.list();

		double[] won = dealSeries(scope, "WON", "count(*)", start, end);
		double[] wonBefore = dealSeries(scope, "WON", "count(*)", start.minus(java.time.Duration.between(start, end)), start);
		double[] revenue = dealSeries(scope, "WON", "coalesce(sum(d.base_amount), 0)", start, end);
		double[] revenueBefore = dealSeries(scope, "WON", "coalesce(sum(d.base_amount), 0)", start.minus(java.time.Duration.between(start, end)), start);
		double average = sum(won) == 0 ? 0 : sum(revenue) / sum(won);
		double averageBefore = sum(wonBefore) == 0 ? 0 : sum(revenueBefore) / sum(wonBefore);
		double[] averageTrend = new double[BUCKETS];
		for (int i = 0; i < BUCKETS; i++) {
			averageTrend[i] = won[i] == 0 ? 0 : revenue[i] / won[i];
		}
		List<Kpi> kpis = kpis(scope, start, end);
		List<Kpi> picked = new ArrayList<>();
		for (String id : List.of("revenue", "won")) {
			picked.add(kpis.stream().filter((k) -> k.id().equals(id)).findFirst().orElseThrow());
		}
		picked.add(new Kpi("average", average, change(averageBefore, average), averageTrend));
		picked.add(kpis.stream().filter((k) -> k.id().equals("conversion")).findFirst().orElseThrow());
		return new Overview(funnel, bySource, team, average, picked);
	}

	private record FunnelRow(long total, long contacted, long qualified, long converted, long won) {
	}

	// ---- building blocks ----

	// Fixed pieces of SQL. They hold no user input, only the named parameters :all and :ids.
	private static final String LEAD_SCOPE = "(:all or l.owner_id in (:ids) or l.owner_id is null)";

	private static final String DEAL_SCOPE = "(:all or d.owner_id in (:ids))";

	private Scope scope(CurrentUser user) {
		return new Scope(this.users.visibleOwnerIds(user).orElse(null));
	}

	private static Instant start(LocalDate day) {
		return day.atStartOfDay().toInstant(ZoneOffset.UTC);
	}

	/** Counts leads per slice of the range, by the given date column. */
	private double[] leadSeries(Scope scope, String column, String extraWhere, Instant from, Instant to) {
		return series("""
				select width_bucket(extract(epoch from %1$s), cast(:lo as numeric), cast(:hi as numeric), %2$d) as b, count(*) as v
				from lead l where l.deleted_at is null and %3$s and %1$s >= :from and %1$s < :to %4$s group by b
				""".formatted(column, BUCKETS, LEAD_SCOPE, extraWhere), scope, from, to);
	}

	/** Leads that moved to Qualified, per slice, from the status history. */
	private double[] qualifiedSeries(Scope scope, Instant from, Instant to) {
		return series("""
				select width_bucket(extract(epoch from h.changed_at), cast(:lo as numeric), cast(:hi as numeric), %1$d) as b, count(*) as v
				from lead_status_history h join lead l on l.id = h.lead_id
				where l.deleted_at is null and %2$s and h.to_status = 'QUALIFIED' and h.changed_at >= :from and h.changed_at < :to
				group by b
				""".formatted(BUCKETS, LEAD_SCOPE), scope, from, to);
	}

	/** Deals that closed in the given stage (WON or LOST), per slice. {@code aggregate} is count(*) or a sum. */
	private double[] dealSeries(Scope scope, String stage, String aggregate, Instant from, Instant to) {
		return series("""
				select width_bucket(extract(epoch from d.closed_at), cast(:lo as numeric), cast(:hi as numeric), %1$d) as b, %2$s as v
				from deal d where d.deleted_at is null and d.stage_code = '%3$s' and %4$s
				  and d.closed_at >= :from and d.closed_at < :to
				group by b
				""".formatted(BUCKETS, aggregate, stage, DEAL_SCOPE), scope, from, to);
	}

	private double[] series(String sql, Scope scope, Instant from, Instant to) {
		double[] values = new double[BUCKETS];
		jdbc.sql(sql)
			.param("all", scope.all()).param("ids", scope.list())
			.param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.param("lo", from.getEpochSecond()).param("hi", to.getEpochSecond())
			.query((rs, i) -> {
				int bucket = rs.getInt("b");
				if (bucket >= 1 && bucket <= BUCKETS) {
					values[bucket - 1] = rs.getDouble("v");
				}
				return 0;
			})
			.list();
		return values;
	}

	private long countLeadsCreatedBefore(Scope scope, Instant before) {
		return jdbc.sql("select count(*) from lead l where l.deleted_at is null and " + LEAD_SCOPE + " and l.created_at < :before")
			.param("all", scope.all()).param("ids", scope.list()).param("before", Timestamp.from(before))
			.query(Long.class).single();
	}

	private static double[] runningTotal(long start, double[] added) {
		double[] total = new double[added.length];
		double running = start;
		for (int i = 0; i < added.length; i++) {
			running += added[i];
			total[i] = running;
		}
		return total;
	}

	private static double[] ratio(double[] top, double[] bottom) {
		double[] result = new double[top.length];
		for (int i = 0; i < top.length; i++) {
			result[i] = percent(top[i], bottom[i]);
		}
		return result;
	}

	private static double sum(double[] values) {
		double total = 0;
		for (double v : values) {
			total += v;
		}
		return total;
	}

	private static double percent(double part, double whole) {
		return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
	}

	/** Percent change from before to now, to one decimal. Null when there was nothing before to compare with. */
	private static Double change(double before, double now) {
		return before == 0 ? null : Math.round((now - before) * 1000.0 / before) / 10.0;
	}

}
