"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { RevenueChart, type RevenuePoint } from "@/components/dashboard/revenue-chart";
import { Card, CardHeader } from "@/components/ui/primitives";

export function RevenueGrowthCard({ points, hasTargets, totalRevenue }: { points: RevenuePoint[]; hasTargets: boolean; totalRevenue: number }) {
  const { reportingCurrency, formatReporting } = useCurrency();

  return (
    <Card className="lg:col-span-2">
      <CardHeader title="Revenue growth" subtitle={`Last 6 months${hasTargets ? ", against target" : ""} · ${reportingCurrency}`} />
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
          {hasTargets && (
            <span className="flex items-center gap-1.5">
              <span className="w-4 border-t-2 border-dashed border-ink-3" aria-hidden /> Target
            </span>
          )}
        </div>
      </div>
      <div className="px-3 pt-2 pb-3">
        {points.length > 0 ? <RevenueChart data={points} /> : <p className="px-2 py-8 text-[13px] text-ink-3">Loading…</p>}
      </div>
    </Card>
  );
}
