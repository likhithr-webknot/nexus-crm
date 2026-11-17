"use client";

import { SessionFirstName } from "@/components/auth/session";
import { useCurrency } from "@/components/currency/currency-provider";
import { FollowUpList } from "@/components/dashboard/follow-ups";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { LeadSourcesDonut } from "@/components/dashboard/lead-sources";
import { PipelineOverview } from "@/components/dashboard/pipeline-overview";
import { RecentLeadsTable } from "@/components/dashboard/recent-leads";
import { RevenueChart } from "@/components/dashboard/revenue-chart";
import { Card, CardHeader, CardLink, StageBadge, control } from "@/components/ui/primitives";
import { useRevenuePoints } from "@/hooks/use-revenue";
import { listFollowUps } from "@/lib/follow-ups";
import { formatDay, localIso, todayLocal, toInstant } from "@/lib/format";
import { listLeads } from "@/lib/leads";
import { KEYS } from "@/lib/query-keys";
import { dashboard } from "@/lib/reports";
import { listAllDeals, listStages } from "@/lib/deals";
import { RANGES, rangeDays, type RangeId } from "@/lib/date-range";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";


/** The block shown while a card's numbers are on their way. */
function Loading({ error, retry }: { error?: boolean; retry?: () => void }) {
  return (
    <p className="px-5 pb-5 text-[13px] text-ink-3" role="status">
      {error ? (
        <>
          Couldn&apos;t load this.{" "}
          <button onClick={retry} className="font-medium text-brand hover:underline">
            Try again
          </button>
        </>
      ) : (
        "Loading…"
      )}
    </p>
  );
}

export function DashboardView() {
  const { reportingCurrency, formatReporting, formatCompactReporting, rateState } = useCurrency();
  const [range, setRange] = useState<RangeId>("month");
  const [from, to] = rangeDays(range);
  const summary = useQuery({ queryKey: [...KEYS.dashboard, "summary", from, to], queryFn: () => dashboard(from, to) });
  const deals = useQuery({ queryKey: [...KEYS.deals, "dashboard"], queryFn: () => listAllDeals() });
  const stages = useQuery({ queryKey: KEYS.stages, queryFn: listStages });
  const revenue = useRevenuePoints();
  const months = revenue.query;
  const recent = useQuery({ queryKey: [...KEYS.leads, "recent"], queryFn: () => listLeads({ size: 6 }) });
  const today = todayLocal();
  const dueNow = useQuery({
    queryKey: [...KEYS.followUps, "dashboard", today],
    queryFn: () => listFollowUps({ status: "OPEN", to: toInstant(today, "23:59"), size: 20 }),
  });

  const data = summary.data?.data;
  const chart = revenue.points;
  const totalRevenue = revenue.totalRevenue;
  const totalTarget = revenue.totalTarget;
  const attainment = totalTarget > 0 ? Math.round((revenue.revenueWithTarget / totalTarget) * 100) : null;
  const pipelineValue = (data?.pipeline ?? []).reduce((a, s) => a + s.amount, 0);
  const rangeLabel = summary.data
    ? `${formatDay(summary.data.from)} – ${formatDay(summary.data.to)}, ${summary.data.to.slice(0, 4)}`
    : "This month";

  const due = (dueNow.data?.items ?? []).sort((a, b) => a.dueAt.localeCompare(b.dueAt));
  const overdue = due.filter((f) => localIso(f.dueAt).slice(0, 10) < today).length;
  const allDeals = deals.data?.items ?? [];
  const dealsCut = deals.data && !deals.data.complete ? `${deals.data.items.length.toLocaleString("en-IN")} of ${deals.data.total.toLocaleString("en-IN")}` : null;
  const stageMap = new Map((stages.data ?? []).map((stage) => [stage.code, stage]));
  const openDeals = allDeals.filter((deal) => stageMap.get(deal.stageCode)?.kind === "OPEN");
  const toBase = (deal: typeof allDeals[number]) => Number(deal.baseAmount ?? Number(deal.amount.amount) / (rateState.status === "ready" ? rateState.rates[deal.amount.currency] ?? 1 : 1));
  const weighted = openDeals.reduce((sum, deal) => sum + toBase(deal) * (stageMap.get(deal.stageCode)?.winProbability ?? 0) / 100, 0);
  const closed = allDeals.filter((deal) => deal.closedAt);
  const won = closed.filter((deal) => deal.stageCode === "WON");
  const winRate = closed.length ? Math.round(won.length / closed.length * 100) : 0;
  const forecastConfidence = pipelineValue ? Math.round(weighted / pipelineValue * 100) : 0;
  const attention = openDeals.filter((deal) => deal.expectedCloseDate && deal.expectedCloseDate <= today).sort((a, b) => (a.expectedCloseDate ?? "").localeCompare(b.expectedCloseDate ?? "")).slice(0, 4);
  const recentlyClosed = [...closed].sort((a, b) => (b.closedAt ?? "").localeCompare(a.closedAt ?? "")).slice(0, 4);
  const performerRows = [...new Map(allDeals.map((deal) => [deal.owner.id, deal.owner.name])).entries()].map(([id, name]) => ({ id, name, revenue: won.filter((deal) => deal.owner.id === id).reduce((sum, deal) => sum + toBase(deal), 0), wins: won.filter((deal) => deal.owner.id === id).length })).sort((a, b) => b.revenue - a.revenue).slice(0, 3);

  return (
    <div className="mx-auto max-w-[1600px] space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[24px] font-semibold tracking-[-0.025em] text-ink">
            Welcome back, <SessionFirstName />
          </h1>
          <p className="mt-1 text-[14px] text-ink-2">Here&apos;s how your pipeline is tracking this month.</p>
        </div>
        <div className="flex items-center gap-2">
          <select aria-label="Dashboard date range" value={range} onChange={(event) => setRange(event.target.value as RangeId)} className={control + " px-2.5"}>{Object.entries(RANGES).map(([id, item]) => <option key={id} value={id}>{item.label}</option>)}</select>
          <p className="inline-flex h-9 items-center gap-2 px-1 text-[13px] font-medium text-ink-2">
            <CalendarDays className="size-4" aria-hidden />
            {rangeLabel}
          </p>
          <Link
            href="/leads"
            className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-brand px-3.5 text-[13px] font-semibold text-white shadow-sm hover:bg-brand-ink"
          >
            <Plus className="size-4" aria-hidden />
            Add lead
          </Link>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Insight label="Weighted pipeline" value={deals.isPending || stages.isPending ? "…" : rateState.status !== "ready" ? "—" : formatCompactReporting(weighted, "INR") ?? "—"} note="Stage probability applied to open value" />
        <Insight label="Active opportunities" value={deals.isPending ? "…" : String(openDeals.length)} note="Open deals across all stages" />
        <Insight label="Win rate" value={deals.isPending ? "…" : `${winRate}%`} note={`${won.length} won of ${closed.length} closed deals`} />
        <Insight label="Forecast confidence" value={deals.isPending || stages.isPending ? "…" : `${forecastConfidence}%`} note="Weighted share of open pipeline" />
      </div>
      {dealsCut && <p className="-mt-2 text-[12.5px] text-ink-3" role="status">Deal figures above use the {dealsCut} most recently updated deals. Open Deals to see them all.</p>}

      {summary.isError ? (
        <Card>
          <div className="pt-5">
            <Loading error retry={() => summary.refetch()} />
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 2xl:grid-cols-7">
          {(data?.kpis ?? []).map((kpi) => (
            <KpiCard key={kpi.id} kpi={kpi} className={kpi.id === "revenue" ? "col-span-2 2xl:col-span-1" : undefined} />
          ))}
          {!data &&
            Array.from({ length: 7 }, (_, i) => (
              <div key={i} className="h-[104px] animate-pulse rounded-xl border border-line bg-surface motion-reduce:animate-none" aria-hidden />
            ))}
        </div>
      )}

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Revenue performance"
            subtitle={`Monthly revenue${revenue.hasTargets ? " against target" : ""} · ${reportingCurrency}`}
            action={<CardLink href="/reports">Last 6 months</CardLink>}
          />
          {months.isError ? (
            <Loading error retry={() => months.refetch()} />
          ) : !months.data ? (
            <Loading />
          ) : (
            <>
              <div className="flex flex-wrap items-end justify-between gap-4 px-5">
                <div>
                  <p className="text-[12px] font-medium text-ink-3">Revenue achieved</p>
                  <p className="tnum mt-1 text-[26px] leading-none font-semibold tracking-[-0.02em]">
                    {formatReporting(totalRevenue, "INR") ?? "—"}
                  </p>
                  {attainment != null && (
                    <p className="mt-1.5 text-[12px] text-ink-3">
                      <span className="font-semibold text-up">{attainment}%</span> of {formatReporting(totalTarget, "INR") ?? "—"} target
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-4 text-[12px] text-ink-2">
                  <span className="flex items-center gap-1.5">
                    <span className="h-0.5 w-4 rounded bg-brand" aria-hidden /> Revenue
                  </span>
                  {revenue.hasTargets && (
                    <span className="flex items-center gap-1.5">
                      <span className="w-4 border-t-2 border-dashed border-ink-3" aria-hidden /> Target
                    </span>
                  )}
                </div>
              </div>
              <div className="px-3 pt-2 pb-3">
                <RevenueChart data={chart} />
              </div>
            </>
          )}
        </Card>

        <Card>
          <CardHeader title="Lead sources" subtitle="Distribution by channel" action={<CardLink href="/reports">This month</CardLink>} />
          <div className="px-5 pt-2 pb-5">
            {!data ? (
              <Loading error={summary.isError} retry={() => summary.refetch()} />
            ) : data.leadSources.length === 0 ? (
              <p className="text-[13px] text-ink-3">No leads were added this month.</p>
            ) : (
              <LeadSourcesDonut
                sources={data.leadSources}
                total={String(data.leadSources.reduce((a, s) => a + s.count, 0))}
              />
            )}
          </div>
        </Card>
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card><CardHeader title="Deals requiring attention" subtitle="Expected close date has passed" action={<CardLink href="/deals">All deals</CardLink>} />{deals.isPending ? <Loading /> : attention.length ? <ul className="divide-y divide-line">{attention.map((deal) => <li key={deal.id} className="px-5 py-3 text-[12.5px]"><Link href={`/deals/${deal.id}`} className="font-medium hover:text-brand">{deal.name}</Link><p className="mt-1 text-ink-3">{deal.company.name} · Due {deal.expectedCloseDate ? formatDay(deal.expectedCloseDate) : "—"}</p></li>)}</ul> : <p className="px-5 pb-5 text-[12.5px] text-ink-3">No overdue opportunities.</p>}</Card>
        <Card><CardHeader title="Recently closed" subtitle="Latest outcomes" action={<CardLink href="/deals">Open deals</CardLink>} />{deals.isPending ? <Loading /> : recentlyClosed.length ? <ul className="divide-y divide-line">{recentlyClosed.map((deal) => <li key={deal.id} className="flex items-center justify-between gap-2 px-5 py-3 text-[12.5px]"><Link href={`/deals/${deal.id}`} className="min-w-0 truncate font-medium hover:text-brand">{deal.name}</Link><StageBadge stage={deal.stageCode} /></li>)}</ul> : <p className="px-5 pb-5 text-[12.5px] text-ink-3">No closed deals in this workspace.</p>}</Card>
        <Card><CardHeader title="Top representatives" subtitle="Revenue from won opportunities" action={<CardLink href="/reports">Team report</CardLink>} />{deals.isPending ? <Loading /> : performerRows.length ? <ol className="divide-y divide-line">{performerRows.map((person, index) => <li key={person.id} className="flex items-center gap-3 px-5 py-3 text-[12.5px]"><span className="tnum text-ink-3">{index + 1}</span><span className="min-w-0 flex-1 truncate font-medium">{person.name}</span><span className="tnum text-ink-2">{formatReporting(person.revenue, "INR") ?? "—"}</span></li>)}</ol> : <p className="px-5 pb-5 text-[12.5px] text-ink-3">No won revenue yet.</p>}</Card>
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="overflow-hidden lg:col-span-2">
          <CardHeader
            title="Recent leads"
            subtitle="Latest additions to your pipeline"
            action={<CardLink href="/leads">View all leads</CardLink>}
          />
          {recent.isError ? (
            <Loading error retry={() => recent.refetch()} />
          ) : !recent.data ? (
            <Loading />
          ) : (
            <RecentLeadsTable leads={recent.data.items} />
          )}
        </Card>

        <div className="space-y-5">
          <Card>
            <CardHeader
              title="Pipeline overview"
              subtitle={`${formatCompactReporting(pipelineValue, "INR") ?? "—"} open pipeline value`}
              action={<CardLink href="/pipeline">View pipeline</CardLink>}
            />
            <div className="px-5 pt-1 pb-5">
              {!data ? <Loading error={summary.isError} retry={() => summary.refetch()} /> : <PipelineOverview stages={data.pipeline} />}
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Today's follow-ups"
              subtitle={`${due.length - overdue} due today · ${overdue} overdue`}
              action={<CardLink href="/calendar">All</CardLink>}
            />
            {dueNow.isError ? <Loading error retry={() => dueNow.refetch()} /> : <FollowUpList tasks={due} />}
          </Card>
        </div>
      </div>
    </div>
  );
}

function Insight({ label, value, note }: { label: string; value: string; note: string }) {
  return <Card className="p-4"><p className="text-[11.5px] font-medium text-ink-3">{label}</p><p className="tnum mt-2 text-[22px] font-semibold tracking-tight text-ink">{value}</p><p className="mt-1 text-[11px] text-ink-3">{note}</p></Card>;
}
