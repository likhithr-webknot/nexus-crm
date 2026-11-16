"use client";

import { Download } from "lucide-react";
import { FUNNEL, KPIS, REVENUE, REVENUE_BY_SOURCE, TEAM, type Kpi } from "@/lib/mock-data";
import { Avatar, Card, CardHeader, Meter, Page, PageHeader, buttonSecondary } from "@/components/ui/primitives";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { RevenueChart } from "@/components/dashboard/revenue-chart";
import { downloadCsv } from "@/lib/csv";
import { formatUsd } from "@/lib/format";

const RING_R = 54;
const RING_C = 2 * Math.PI * RING_R;

export function ReportsView() {
  const totalRevenue = REVENUE.reduce((a, r) => a + r.revenue, 0);
  const kpi = (id: string) => KPIS.find((k) => k.id === id)!;
  const dealsWon = Number(kpi("won").value);
  const kpis: Kpi[] = [
    kpi("revenue"),
    kpi("won"),
    { id: "avg", label: "Avg. deal size", value: formatUsd(Math.round(totalRevenue / dealsWon)), delta: 9.1, trend: [6, 6, 7, 6, 8, 8, 9] },
    kpi("conversion"),
  ];

  const month = REVENUE.at(-1)!;
  const attained = Math.round((month.revenue / month.target) * 100);
  const gap = month.revenue - month.target;

  const sellers = TEAM.filter((m) => m.leads > 0);
  const topSource = Math.max(...REVENUE_BY_SOURCE.map((s) => s.revenue));
  const sourceTotal = REVENUE_BY_SOURCE.reduce((a, s) => a + s.revenue, 0);

  const exportTeam = () =>
    downloadCsv(
      "team-performance.csv",
      ["Sales executive", "Leads", "Deals won", "Revenue (USD)", "Conversion %", "Quota attainment %"],
      sellers.map((m) => [m.name, m.leads, m.dealsWon, m.revenue, m.conversion, m.attainment]),
    );

  return (
    <Page>
      <PageHeader title="Reports & Analytics" subtitle="Sales results for January to June 2025.">
        <button onClick={exportTeam} className={buttonSecondary}>
          <Download className="size-4" aria-hidden />
          Export team report
        </button>
      </PageHeader>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {kpis.map((k) => (
          <KpiCard key={k.id} kpi={k} />
        ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Revenue growth" subtitle="Monthly revenue against target" />
          <div className="flex flex-wrap items-end justify-between gap-4 px-5">
            <div>
              <p className="text-[12px] font-medium text-ink-3">Revenue achieved</p>
              <p className="tnum mt-1 text-[26px] leading-none font-semibold tracking-[-0.02em]">{formatUsd(totalRevenue)}</p>
            </div>
            <div className="flex items-center gap-4 text-[12px] text-ink-2">
              <span className="flex items-center gap-1.5">
                <span className="h-0.5 w-4 rounded bg-brand" aria-hidden /> Revenue
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-4 border-t-2 border-dashed border-slate-400" aria-hidden /> Target
              </span>
            </div>
          </div>
          <div className="px-3 pt-2 pb-3">
            <RevenueChart data={REVENUE} />
          </div>
        </Card>

        <Card>
          <CardHeader title="Sales target" subtitle={`${month.month} 2025`} />
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
              {gap >= 0 ? `Target beaten by ${formatUsd(gap)}` : `${formatUsd(-gap)} still to go`}
            </p>
            <dl className="mt-4 grid w-full grid-cols-2 divide-x divide-line border-t border-line pt-4 text-center">
              <div>
                <dt className="text-[12px] text-ink-3">Achieved</dt>
                <dd className="tnum mt-0.5 text-[15px] font-semibold">{formatUsd(month.revenue)}</dd>
              </div>
              <div>
                <dt className="text-[12px] text-ink-3">Target</dt>
                <dd className="tnum mt-0.5 text-[15px] font-semibold">{formatUsd(month.target)}</dd>
              </div>
            </dl>
          </div>
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
                <th className="w-56 py-2.5 pr-5 pl-6 font-medium">Quota attainment</th>
              </tr>
            </thead>
            <tbody className="tnum divide-y divide-line">
              {sellers.map((m) => (
                <tr key={m.id} className="hover:bg-canvas/60">
                  <td className="py-3 pr-3 pl-5">
                    <div className="flex items-center gap-3">
                      <Avatar name={m.name} />
                      <span className="font-medium text-ink">{m.name}</span>
                    </div>
                  </td>
                  <td className="px-3 py-3 text-right text-ink-2">{m.leads}</td>
                  <td className="px-3 py-3 text-right text-ink-2">{m.dealsWon}</td>
                  <td className="px-3 py-3 text-right font-medium text-ink">{formatUsd(m.revenue)}</td>
                  <td className="px-3 py-3 text-right text-ink-2">{m.conversion}%</td>
                  <td className="py-3 pr-5 pl-6">
                    <div className="flex items-center gap-3">
                      <Meter value={m.attainment} />
                      <span className="w-9 text-right font-medium text-ink">{m.attainment}%</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Lead conversion funnel" subtitle="How many leads reach each step, and how many are kept from the step before" />
          <ol className="space-y-3.5 px-5 pt-1 pb-5">
            {FUNNEL.map((step, i) => {
              const ofAll = (step.count / FUNNEL[0].count) * 100;
              const kept = i > 0 ? Math.round((step.count / FUNNEL[i - 1].count) * 100) : null;
              return (
                <li key={step.label} className="grid grid-cols-[88px_minmax(0,1fr)_auto] items-center gap-x-4 text-[13px]">
                  <span className="font-medium text-ink">{step.label}</span>
                  <Meter value={ofAll} className={i === FUNNEL.length - 1 ? "bg-up" : undefined} />
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
            {REVENUE_BY_SOURCE.map((s) => (
              <li key={s.label} className="text-[13px]">
                <div className="mb-1.5 flex items-baseline justify-between">
                  <span className="font-medium text-ink">{s.label}</span>
                  <span className="tnum text-ink-3">
                    <span className="font-semibold text-ink">{formatUsd(s.revenue)}</span> · {Math.round((s.revenue / sourceTotal) * 100)}%
                  </span>
                </div>
                <Meter value={(s.revenue / topSource) * 100} />
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </Page>
  );
}
