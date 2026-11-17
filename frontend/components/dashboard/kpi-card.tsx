"use client";

import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import type { Kpi } from "@/lib/reports";
import { useCurrency } from "@/components/currency/currency-provider";
import { useCountUp } from "@/hooks/use-count-up";
import { cn } from "@/lib/format";

/** What each number is called, and whether a fall in it is good news. */
const KPI_INFO: Record<Kpi["id"], { label: string; invert?: boolean }> = {
  total: { label: "Total Leads" },
  new: { label: "New Leads" },
  qualified: { label: "Qualified Leads" },
  won: { label: "Deals Won" },
  lost: { label: "Deals Lost", invert: true },
  revenue: { label: "Revenue" },
  conversion: { label: "Conversion Rate" },
  average: { label: "Avg. deal size" },
};

function Sparkline({ data, positive }: { data: number[]; positive: boolean }) {
  const w = 72;
  const h = 28;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const span = max - min || 1;
  const pts = data.map((v, i) => [(i / (data.length - 1)) * w, h - 2 - ((v - min) / span) * (h - 4)]);
  const line = pts.map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(1)},${y.toFixed(1)}`).join("");
  const color = positive ? "var(--color-up)" : "var(--color-down)";
  return (
    <svg
      viewBox={`0 0 ${w} ${h}`}
      preserveAspectRatio="none"
      aria-hidden
      className="h-7 w-12 shrink-0 @[176px]:w-[72px]"
    >
      <path d={`${line}L${w},${h}L0,${h}Z`} fill={color} opacity={0.08} />
      <path
        d={line}
        fill="none"
        stroke={color}
        strokeWidth={1.75}
        vectorEffect="non-scaling-stroke"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function KpiCard({ kpi, className }: { kpi: Kpi; className?: string }) {
  const { formatCompactReporting, formatReporting } = useCurrency();
  const info = KPI_INFO[kpi.id];
  const money = kpi.id === "revenue" || kpi.id === "average";
  // The number counts up to its value; a percentage keeps the decimals the real value has.
  const counted = useCountUp(kpi.value);
  const decimals = Number.isInteger(kpi.value) ? 0 : 1;
  const value = money
    ? ((kpi.id === "revenue" ? formatCompactReporting(counted, "INR") : formatReporting(Math.round(counted), "INR")) ??
      "—")
    : kpi.id === "conversion"
      ? `${counted.toFixed(decimals)}%`
      : Math.round(counted).toLocaleString("en-US");
  const change = kpi.change;
  // "Good" direction depends on the metric: fewer lost deals is positive.
  const good = info.invert ? (change ?? 0) <= 0 : (change ?? 0) >= 0;
  const Arrow = (change ?? 0) >= 0 ? ArrowUpRight : ArrowDownRight;
  // A line needs two points to be drawn.
  const trend = kpi.trend.length > 1 ? kpi.trend : [0, 0];

  return (
    <div className={cn("@container rounded-xl border border-line bg-surface px-4 py-3.5", className)}>
      <p className="truncate text-[12.5px] font-medium text-ink-2">{info.label}</p>
      <div className="mt-2 flex items-end justify-between gap-2">
        <p
          className="tnum text-[24px] leading-none font-semibold tracking-[-0.02em] text-ink"
          aria-label={`${info.label}: ${value}`}
        >
          {value}
        </p>
        <Sparkline data={trend} positive={good} />
      </div>
      <p className="mt-2.5 flex items-center gap-1 overflow-hidden text-[12px] whitespace-nowrap text-ink-3">
        {change == null ? (
          <span className="truncate">No earlier period to compare</span>
        ) : (
          <>
            <span className={cn("tnum inline-flex items-center font-semibold", good ? "text-up" : "text-down")}>
              <Arrow className="size-3.5" aria-hidden />
              {Math.abs(change)}%
            </span>
            <span className="truncate">vs previous period</span>
          </>
        )}
      </p>
    </div>
  );
}
