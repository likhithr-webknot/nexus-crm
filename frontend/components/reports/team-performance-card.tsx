"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { Avatar, Card, CardHeader, Meter } from "@/components/ui/primitives";
import type { Overview } from "@/lib/reports";

export function TeamPerformanceCard({ team, loaded }: { team: Overview["team"]; loaded: boolean }) {
  const { formatReporting } = useCurrency();
  const topRevenue = Math.max(1, ...team.map((m) => m.revenue));

  return (
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
            {team.map((m) => (
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
        {loaded && team.length === 0 && <p className="px-5 py-6 text-[13px] text-ink-3">No leads or won deals in this range.</p>}
      </div>
    </Card>
  );
}
