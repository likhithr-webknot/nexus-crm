import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import type { Kpi } from "@/lib/mock-data";
import { cn } from "@/lib/format";

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
      <path d={line} fill="none" stroke={color} strokeWidth={1.75} vectorEffect="non-scaling-stroke" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function KpiCard({ kpi }: { kpi: Kpi }) {
  // "Good" direction depends on the metric: fewer lost deals is positive.
  const good = kpi.invert ? kpi.delta <= 0 : kpi.delta >= 0;
  const Arrow = kpi.delta >= 0 ? ArrowUpRight : ArrowDownRight;

  return (
    <div className="@container rounded-xl border border-line bg-surface px-4 py-3.5">
      <p className="truncate text-[12.5px] font-medium text-ink-2">{kpi.label}</p>
      <div className="mt-2 flex items-end justify-between gap-2">
        <p className="tnum text-[24px] leading-none font-semibold tracking-[-0.02em] text-ink">{kpi.value}</p>
        <Sparkline data={kpi.trend} positive={good} />
      </div>
      <p className="mt-2.5 flex items-center gap-1 overflow-hidden text-[12px] whitespace-nowrap text-ink-3">
        <span className={cn("tnum inline-flex items-center font-semibold", good ? "text-up" : "text-down")}>
          <Arrow className="size-3.5" aria-hidden />
          {Math.abs(kpi.delta)}%
        </span>
        <span className="truncate">vs last month</span>
      </p>
    </div>
  );
}
