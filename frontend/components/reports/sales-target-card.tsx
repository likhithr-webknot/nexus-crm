"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { type RevenuePoint } from "@/components/dashboard/revenue-chart";
import { Card, CardHeader } from "@/components/ui/primitives";

const RING_R = 54;
const RING_C = 2 * Math.PI * RING_R;

export function SalesTargetCard({ month }: { month: RevenuePoint | undefined }) {
  const { formatReporting } = useCurrency();
  const hasTarget = month?.target != null && month.target > 0;
  const attained = hasTarget ? Math.round((month!.revenue / month!.target!) * 100) : 0;
  const gap = hasTarget ? month!.revenue - month!.target! : 0;

  return (
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
  );
}
