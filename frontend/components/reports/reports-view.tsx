"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { LoadFailed } from "@/components/ui/load-state";
import { Card, Page, PageHeader, buttonSecondary, control } from "@/components/ui/primitives";
import { useRevenuePoints } from "@/hooks/use-revenue";
import { downloadCsv } from "@/lib/csv";
import { RANGES, rangeDays, type RangeId } from "@/lib/date-range";
import { cn, formatDay } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { overview } from "@/lib/reports";
import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { useState } from "react";
import dynamic from "next/dynamic";
import { FunnelCard } from "./funnel-card";
import { RevenueBySourceCard } from "./revenue-by-source-card";
import { RevenueGrowthCard } from "./revenue-growth-card";
import { SalesTargetCard } from "./sales-target-card";
import { TeamPerformanceCard } from "./team-performance-card";
const ReportBuilder = dynamic(() => import("./report-builder").then((module) => module.ReportBuilder), { ssr: false });

export function ReportsView() {
  const { reportingCurrency, convertToReporting } = useCurrency();
  const [range, setRange] = useState<RangeId>("month");
  const [from, to] = rangeDays(range);

  const report = useQuery({ queryKey: [...KEYS.overview, from, to], queryFn: () => overview(from, to) });
  const revenue = useRevenuePoints();
  const data = report.data;
  const sellers = (data?.team ?? []).filter((m) => m.leads > 0 || m.dealsWon > 0);

  const exportTeam = () =>
    downloadCsv(
      "team-performance.csv",
      ["Sales executive", "Leads", "Deals won", `Revenue (${reportingCurrency})`, "Conversion %"],
      sellers.map((m) => [
        m.name,
        m.leads,
        m.dealsWon,
        convertToReporting(m.revenue, "INR") ?? "Exchange rate unavailable",
        m.conversion,
      ]),
    );

  if (report.isError) {
    return (
      <Page>
        <PageHeader title="Reports & Analytics" subtitle="Sales results." />
        <Card>
          <LoadFailed what="the reports" onRetry={() => report.refetch()} />
        </Card>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader title="Reports & Analytics" subtitle={`Sales results, ${formatDay(from)} to ${formatDay(to)}.`}>
        <select
          aria-label="Date range"
          value={range}
          onChange={(e) => setRange(e.target.value as RangeId)}
          className={cn(control, "px-2.5")}
        >
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
            <div
              key={i}
              className="h-[104px] animate-pulse rounded-xl border border-line bg-surface motion-reduce:animate-none"
              aria-hidden
            />
          ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <RevenueGrowthCard
          points={revenue.points}
          hasTargets={revenue.hasTargets}
          totalRevenue={revenue.totalRevenue}
        />
        <SalesTargetCard month={revenue.points.at(-1)} />
      </div>

      <TeamPerformanceCard team={sellers} loaded={Boolean(data)} />

      <div className="grid gap-5 lg:grid-cols-3">
        <FunnelCard funnel={data?.funnel ?? []} />
        <RevenueBySourceCard sources={data?.revenueBySource ?? []} loaded={Boolean(data)} />
      </div>
      <ReportBuilder overview={data} revenue={revenue.points} />
    </Page>
  );
}
