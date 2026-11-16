"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, Plus } from "lucide-react";
import { SessionFirstName } from "@/components/auth/session";
import { Card, CardHeader, CardLink } from "@/components/ui/primitives";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { RevenueChart, type RevenuePoint } from "@/components/dashboard/revenue-chart";
import { LeadSourcesDonut } from "@/components/dashboard/lead-sources";
import { RecentLeadsTable } from "@/components/dashboard/recent-leads";
import { PipelineOverview } from "@/components/dashboard/pipeline-overview";
import { FollowUpList } from "@/components/dashboard/follow-ups";
import { useCurrency } from "@/components/currency/currency-provider";
import { dashboard, revenueByMonth } from "@/lib/reports";
import { listLeads } from "@/lib/leads";
import { listFollowUps } from "@/lib/follow-ups";
import { formatDay, localIso, todayLocal, toInstant } from "@/lib/format";

const monthName = new Intl.DateTimeFormat("en-US", { month: "short", timeZone: "UTC" });
const monthFull = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" });

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
  const { reportingCurrency, formatReporting, formatCompactReporting } = useCurrency();
  const summary = useQuery({ queryKey: ["dashboard", "summary"], queryFn: () => dashboard() });
  const months = useQuery({ queryKey: ["dashboard", "revenue"], queryFn: () => revenueByMonth(6) });
  const recent = useQuery({ queryKey: ["leads", "recent"], queryFn: () => listLeads({ size: 6 }) });
  const today = todayLocal();
  const dueNow = useQuery({
    queryKey: ["follow-ups", "dashboard", today],
    queryFn: () => listFollowUps({ status: "OPEN", to: toInstant(today, "23:59"), size: 20 }),
  });

  const data = summary.data?.data;
  const chart: RevenuePoint[] = (months.data ?? []).map((m) => ({
    month: monthFull.format(new Date(`${m.month}-01T00:00:00Z`)),
    label: monthName.format(new Date(`${m.month}-01T00:00:00Z`)),
    revenue: m.revenue,
    target: m.target == null ? null : Number(m.target),
  }));
  const totalRevenue = chart.reduce((a, r) => a + r.revenue, 0);
  const withTarget = chart.filter((r) => r.target != null);
  const totalTarget = withTarget.reduce((a, r) => a + (r.target ?? 0), 0);
  const attainment = totalTarget > 0 ? Math.round((withTarget.reduce((a, r) => a + r.revenue, 0) / totalTarget) * 100) : null;
  const pipelineValue = (data?.pipeline ?? []).reduce((a, s) => a + s.amount, 0);
  const range = summary.data
    ? `${formatDay(summary.data.from)} – ${formatDay(summary.data.to)}, ${summary.data.to.slice(0, 4)}`
    : "This month";

  const due = (dueNow.data?.items ?? []).sort((a, b) => a.dueAt.localeCompare(b.dueAt));
  const overdue = due.filter((f) => localIso(f.dueAt).slice(0, 10) < today).length;

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
          <p className="inline-flex h-9 items-center gap-2 px-1 text-[13px] font-medium text-ink-2">
            <CalendarDays className="size-4" aria-hidden />
            {range}
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
            subtitle={`Monthly revenue${withTarget.length ? " against target" : ""} · ${reportingCurrency}`}
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
                  {withTarget.length > 0 && (
                    <span className="flex items-center gap-1.5">
                      <span className="w-4 border-t-2 border-dashed border-slate-400" aria-hidden /> Target
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
              action={<CardLink href="/follow-ups">All</CardLink>}
            />
            {dueNow.isError ? <Loading error retry={() => dueNow.refetch()} /> : <FollowUpList tasks={due} />}
          </Card>
        </div>
      </div>
    </div>
  );
}
