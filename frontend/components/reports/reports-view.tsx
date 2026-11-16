"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, WifiOff } from "lucide-react";
import { Avatar, Card, CardHeader, EmptyState, Meter, Page, PageHeader, buttonSecondary, control } from "@/components/ui/primitives";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { RevenueChart, type RevenuePoint } from "@/components/dashboard/revenue-chart";
import { downloadCsv } from "@/lib/csv";
import { cn, formatDay } from "@/lib/format";
import { useCurrency } from "@/components/currency/currency-provider";
import { overview, revenueByMonth } from "@/lib/reports";
import { sourceLabel } from "@/lib/stages";

const RING_R = 54;
const RING_C = 2 * Math.PI * RING_R;
const monthName = new Intl.DateTimeFormat("en-US", { month: "short", timeZone: "UTC" });
const monthFull = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" });

const iso = (d: Date) => d.toISOString().slice(0, 10);

/** The date ranges you can report on. All in UTC, like the server. */
const RANGES = {
  month: { label: "This month", get: (n: Date) => [new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth(), 1)), new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth() + 1, 0))] },
  lastMonth: { label: "Last month", get: (n: Date) => [new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth() - 1, 1)), new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth(), 0))] },
  quarter: { label: "Last 90 days", get: (n: Date) => [new Date(n.getTime() - 89 * 86_400_000), n] },
  year: { label: "This year", get: (n: Date) => [new Date(Date.UTC(n.getUTCFullYear(), 0, 1)), n] },
} as const;

export function ReportsView() {
  const { reportingCurrency, formatReporting, convertToReporting } = useCurrency();
  const [range, setRange] = useState<keyof typeof RANGES>("month");
  const [from, to] = RANGES[range].get(new Date()).map(iso);

  const report = useQuery({ queryKey: ["overview", from, to], queryFn: () => overview(from, to) });
  const months = useQuery({ queryKey: ["dashboard", "revenue"], queryFn: () => revenueByMonth(6) });
  const data = report.data;

  const chart: RevenuePoint[] = (months.data ?? []).map((m) => ({
    month: monthFull.format(new Date(`${m.month}-01T00:00:00Z`)),
    label: monthName.format(new Date(`${m.month}-01T00:00:00Z`)),
    revenue: m.revenue,
    target: m.target == null ? null : Number(m.target),
  }));
  const totalRevenue = chart.reduce((a, r) => a + r.revenue, 0);
  const month = chart.at(-1);
  const hasTarget = month?.target != null && month.target > 0;
  const attained = hasTarget ? Math.round((month!.revenue / month!.target!) * 100) : 0;
  const gap = hasTarget ? month!.revenue - month!.target! : 0;

  const team = data?.team ?? [];
  const sellers = team.filter((m) => m.leads > 0 || m.dealsWon > 0);
  const topRevenue = Math.max(1, ...sellers.map((m) => m.revenue));
  const sources = data?.revenueBySource ?? [];
  const topSource = Math.max(1, ...sources.map((s) => s.revenue));
  const sourceTotal = sources.reduce((a, s) => a + s.revenue, 0);

  const exportTeam = () =>
    downloadCsv(
      "team-performance.csv",
      ["Sales executive", "Leads", "Deals won", `Revenue (${reportingCurrency})`, "Conversion %"],
      sellers.map((m) => [m.name, m.leads, m.dealsWon, convertToReporting(m.revenue, "INR") ?? "Exchange rate unavailable", m.conversion]),
    );

  if (report.isError) {
    return (
      <Page>
        <PageHeader title="Reports & Analytics" subtitle="Sales results." />
        <Card>
          <EmptyState icon={WifiOff} title="Couldn't load the reports" body="Check your connection, then try again.">
            <button onClick={() => report.refetch()} className={buttonSecondary}>
              Try again
            </button>
          </EmptyState>
        </Card>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader title="Reports & Analytics" subtitle={`Sales results, ${formatDay(from)} to ${formatDay(to)}.`}>
        <select aria-label="Date range" value={range} onChange={(e) => setRange(e.target.value as keyof typeof RANGES)} className={cn(control, "px-2.5")}>
          {Object.entries(RANGES).map(([id, r]) => (
            <option key={id} value={id}>
              {r.label}
            </option>
          ))}
        </select>
        <button onClick={exportTeam} disabled={sellers.length === 0} className={buttonSecondary}>
          <Download className="size-4" aria-hidden />
          Export team report
        </button>
      </PageHeader>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {(data?.kpis ?? []).map((k) => (
          <KpiCard key={k.id} kpi={k} />
        ))}
        {!data &&
          Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="h-[104px] animate-pulse rounded-xl border border-line bg-surface motion-reduce:animate-none" aria-hidden />
          ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Revenue growth" subtitle={`Last 6 months${chart.some((c) => c.target != null) ? ", against target" : ""} · ${reportingCurrency}`} />
          <div className="flex flex-wrap items-end justify-between gap-4 px-5">
            <div>
              <p className="text-[12px] font-medium text-ink-3">Revenue achieved</p>
              <p className="tnum mt-1 text-[26px] leading-none font-semibold tracking-[-0.02em]">
                {formatReporting(totalRevenue, "INR") ?? "—"}
              </p>
            </div>
            <div className="flex items-center gap-4 text-[12px] text-ink-2">
              <span className="flex items-center gap-1.5">
                <span className="h-0.5 w-4 rounded bg-brand" aria-hidden /> Revenue
              </span>
              {chart.some((c) => c.target != null) && (
                <span className="flex items-center gap-1.5">
                  <span className="w-4 border-t-2 border-dashed border-slate-400" aria-hidden /> Target
                </span>
              )}
            </div>
          </div>
          <div className="px-3 pt-2 pb-3">
            {chart.length > 0 ? <RevenueChart data={chart} /> : <p className="px-2 py-8 text-[13px] text-ink-3">Loading…</p>}
          </div>
        </Card>

        <Card>
          <CardHeader title="Sales target" subtitle={month?.month ?? ""} />
          {!hasTarget ? (
            <p className="px-5 pt-2 pb-6 text-[13px] text-ink-3">
              No target is set for this month, or you can&apos;t see company targets. An admin can set one under Settings.
            </p>
          ) : (
            <div className="flex flex-col items-center px-5 pt-2 pb-5">
              <div className="relative size-44">
                <svg viewBox="0 0 140 140" className="size-full -rotate-90" aria-hidden>
                  <circle cx={70} cy={70} r={RING_R} fill="none" stroke="var(--color-canvas)" strokeWidth={14} />
                  <circle
                    cx={70}
                    cy={70}
                    r={RING_R}
                    fill="none"
                    stroke={gap >= 0 ? "var(--color-up)" : "var(--color-brand)"}
                    strokeWidth={14}
                    strokeLinecap="round"
                    strokeDasharray={`${(Math.min(attained, 100) / 100) * RING_C} ${RING_C}`}
                  />
                </svg>
                <div className="absolute inset-0 grid place-content-center text-center">
                  <p className="tnum text-[30px] leading-none font-semibold tracking-[-0.03em]">{attained}%</p>
                  <p className="mt-1.5 text-[12px] text-ink-3">of target</p>
                </div>
              </div>
              <p className="mt-4 text-[13px] font-medium text-ink">
                {gap >= 0
                  ? `Target beaten by ${formatReporting(gap, "INR") ?? "—"}`
                  : `${formatReporting(-gap, "INR") ?? "—"} still to go`}
              </p>
              <dl className="mt-4 grid w-full grid-cols-2 divide-x divide-line border-t border-line pt-4 text-center">
                <div>
                  <dt className="text-[12px] text-ink-3">Achieved</dt>
                  <dd className="tnum mt-0.5 text-[15px] font-semibold">{formatReporting(month!.revenue, "INR") ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-[12px] text-ink-3">Target</dt>
                  <dd className="tnum mt-0.5 text-[15px] font-semibold">{formatReporting(month!.target!, "INR") ?? "—"}</dd>
                </div>
              </dl>
            </div>
          )}
        </Card>
      </div>

      <Card className="overflow-hidden">
        <CardHeader title="Team performance" subtitle="Results by sales executive" />
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-[13px]">
            <thead>
              <tr className="border-y border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
                <th className="py-2.5 pr-3 pl-5 font-medium">Sales executive</th>
                <th className="px-3 py-2.5 text-right font-medium">Leads</th>
                <th className="px-3 py-2.5 text-right font-medium">Deals won</th>
                <th className="px-3 py-2.5 text-right font-medium">Revenue</th>
                <th className="px-3 py-2.5 text-right font-medium">Conversion</th>
                <th className="w-56 py-2.5 pr-5 pl-6 font-medium">Share of top revenue</th>
              </tr>
            </thead>
            <tbody className="tnum divide-y divide-line">
              {sellers.map((m) => (
                <tr key={m.userId} className="hover:bg-canvas/60">
                  <td className="py-3 pr-3 pl-5">
                    <div className="flex items-center gap-3">
                      <Avatar name={m.name} />
                      <span className="font-medium text-ink">{m.name}</span>
                    </div>
                  </td>
                  <td className="px-3 py-3 text-right text-ink-2">{m.leads}</td>
                  <td className="px-3 py-3 text-right text-ink-2">{m.dealsWon}</td>
                  <td className="px-3 py-3 text-right font-medium text-ink">{formatReporting(m.revenue, "INR") ?? "—"}</td>
                  <td className="px-3 py-3 text-right text-ink-2">{m.conversion}%</td>
                  <td className="py-3 pr-5 pl-6">
                    <div className="flex items-center gap-3">
                      <Meter value={(m.revenue / topRevenue) * 100} />
                      <span className="w-9 text-right font-medium text-ink">{Math.round((m.revenue / topRevenue) * 100)}%</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data && sellers.length === 0 && <p className="px-5 py-6 text-[13px] text-ink-3">No leads or won deals in this range.</p>}
        </div>
      </Card>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Lead conversion funnel" subtitle="How many leads reach each step, and how many are kept from the step before" />
          <ol className="space-y-3.5 px-5 pt-1 pb-5">
            {(data?.funnel ?? []).map((step, i, funnel) => {
              const ofAll = funnel[0].count > 0 ? (step.count / funnel[0].count) * 100 : 0;
              const kept = i > 0 && funnel[i - 1].count > 0 ? Math.round((step.count / funnel[i - 1].count) * 100) : null;
              return (
                <li key={step.label} className="grid grid-cols-[88px_minmax(0,1fr)_auto] items-center gap-x-4 text-[13px]">
                  <span className="font-medium text-ink">{step.label}</span>
                  <Meter value={ofAll} className={i === funnel.length - 1 ? "bg-up" : undefined} />
                  <span className="tnum flex w-40 items-baseline justify-end gap-2">
                    <span className="font-semibold text-ink">{step.count.toLocaleString("en-US")}</span>
                    <span className="w-10 text-right text-ink-3">{Math.round(ofAll)}%</span>
                    <span className="w-16 text-right text-[12px] text-ink-3">{kept != null ? `${kept}% kept` : ""}</span>
                  </span>
                </li>
              );
            })}
          </ol>
        </Card>

        <Card>
          <CardHeader title="Revenue by source" subtitle="Where won revenue came from" />
          <ul className="space-y-3.5 px-5 pt-1 pb-5">
            {sources.map((s) => (
              <li key={s.source} className="text-[13px]">
                <div className="mb-1.5 flex items-baseline justify-between">
                  <span className="font-medium text-ink">{sourceLabel(s.source)}</span>
                  <span className="tnum text-ink-3">
                    <span className="font-semibold text-ink">{formatReporting(s.revenue, "INR") ?? "—"}</span> ·{" "}
                    {sourceTotal > 0 ? Math.round((s.revenue / sourceTotal) * 100) : 0}%
                  </span>
                </div>
                <Meter value={(s.revenue / topSource) * 100} />
              </li>
            ))}
          </ul>
          {data && sources.length === 0 && <p className="px-5 pb-5 text-[13px] text-ink-3">No deals were won in this range.</p>}
        </Card>
      </div>
    </Page>
  );
}
