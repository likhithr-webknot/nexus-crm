"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { Card, CardHeader, Meter } from "@/components/ui/primitives";
import type { Overview } from "@/lib/reports";
import { sourceLabel } from "@/lib/stages";

export function RevenueBySourceCard({ sources, loaded }: { sources: Overview["revenueBySource"]; loaded: boolean }) {
  const { formatReporting } = useCurrency();
  const top = Math.max(1, ...sources.map((s) => s.revenue));
  const total = sources.reduce((sum, s) => sum + s.revenue, 0);

  return (
    <Card>
      <CardHeader title="Revenue by source" subtitle="Where won revenue came from" />
      <ul className="space-y-3.5 px-5 pt-1 pb-5">
        {sources.map((s) => (
          <li key={s.source} className="text-[13px]">
            <div className="mb-1.5 flex items-baseline justify-between">
              <span className="font-medium text-ink">{sourceLabel(s.source)}</span>
              <span className="tnum text-ink-3">
                <span className="font-semibold text-ink">{formatReporting(s.revenue, "INR") ?? "—"}</span> ·{" "}
                {total > 0 ? Math.round((s.revenue / total) * 100) : 0}%
              </span>
            </div>
            <Meter value={(s.revenue / top) * 100} />
          </li>
        ))}
      </ul>
      {loaded && sources.length === 0 && (
        <p className="px-5 pb-5 text-[13px] text-ink-3">No deals were won in this range.</p>
      )}
    </Card>
  );
}
