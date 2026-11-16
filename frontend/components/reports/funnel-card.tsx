"use client";

import { Card, CardHeader, Meter } from "@/components/ui/primitives";
import type { Overview } from "@/lib/reports";

export function FunnelCard({ funnel }: { funnel: Overview["funnel"] }) {

  return (
    <Card className="lg:col-span-2">
      <CardHeader title="Lead conversion funnel" subtitle="How many leads reach each step, and how many are kept from the step before" />
      <ol className="space-y-3.5 px-5 pt-1 pb-5">
        {funnel.map((step, i, funnel) => {
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
  );
}
