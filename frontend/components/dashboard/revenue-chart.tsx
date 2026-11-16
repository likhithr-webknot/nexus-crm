"use client";

import { useState } from "react";
import { useCurrency } from "@/components/currency/currency-provider";

/** One month on the chart. Amounts are in INR, the base currency. The target is null when none is set or the user cannot see it. */
export type RevenuePoint = { month: string; label: string; revenue: number; target: number | null };

/** A round step (1, 2, 5 times a power of ten) so the grid lines land on tidy numbers. */
function niceStep(rough: number) {
  const power = 10 ** Math.floor(Math.log10(rough));
  const f = rough / power;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * power;
}

const BASE = "INR";
const W = 720;
const H = 240;
const PAD = { top: 12, right: 16, bottom: 28, left: 44 };

/** Monotone-ish smoothing: horizontal control points, never overshoots in x. */
function smoothPath(pts: [number, number][]) {
  return pts.reduce((d, [x, y], i) => {
    if (i === 0) return `M${x},${y}`;
    const [px, py] = pts[i - 1];
    const cx = (px + x) / 2;
    return `${d}C${cx},${py} ${cx},${y} ${x},${y}`;
  }, "");
}

export function RevenueChart({ data }: { data: RevenuePoint[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const { formatReporting, formatCompactReporting } = useCurrency();

  const peak = Math.max(1, ...data.flatMap((d) => [d.revenue, d.target ?? 0]));
  const step = niceStep(peak / 4);
  const max = Math.ceil(peak / step) * step;
  const ticks = Array.from({ length: Math.round(max / step) + 1 }, (_, i) => i * step);
  const innerW = W - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;
  const x = (i: number) => PAD.left + (data.length === 1 ? 0.5 : i / (data.length - 1)) * innerW;
  const y = (v: number) => PAD.top + innerH - (v / max) * innerH;

  const rev = data.map((d, i) => [x(i), y(d.revenue)] as [number, number]);
  const tgt = data.map((d, i) => [x(i), y(d.target ?? 0)] as [number, number]);
  const hasTarget = data.some((d) => d.target != null);
  const revLine = smoothPath(rev);
  const baseline = PAD.top + innerH;

  const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    const i = data.length === 1 ? 0 : Math.round(((px - PAD.left) / innerW) * (data.length - 1));
    setHover(Math.max(0, Math.min(data.length - 1, i)));
  };

  const active = hover != null ? data[hover] : null;

  return (
    <div className="relative">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="h-auto w-full touch-none select-none"
        role="img"
        aria-label={`Monthly revenue${hasTarget ? " vs target" : ""}, ${data[0].label} to ${data.at(-1)!.label}`}
        onPointerMove={onMove}
        onPointerLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id="rev-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--color-brand)" stopOpacity={0.16} />
            <stop offset="100%" stopColor="var(--color-brand)" stopOpacity={0} />
          </linearGradient>
        </defs>

        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--color-line)" />
            <text x={PAD.left - 10} y={y(t)} dy="0.32em" textAnchor="end" className="fill-ink-3 text-[11px]">
              {formatCompactReporting(t, BASE) ?? "—"}
            </text>
          </g>
        ))}
        {data.map((d, i) => (
          <text key={d.month} x={x(i)} y={H - 6} textAnchor="middle" className="fill-ink-3 text-[11px]">
            {d.label}
          </text>
        ))}

        <path d={`${revLine}L${x(data.length - 1)},${baseline}L${x(0)},${baseline}Z`} fill="url(#rev-fill)" />
        {hasTarget && <path d={smoothPath(tgt)} fill="none" stroke="#94a3b8" strokeWidth={1.5} strokeDasharray="5 5" />}
        <path d={revLine} fill="none" stroke="var(--color-brand)" strokeWidth={2.25} strokeLinecap="round" />

        {hover != null && (
          <line x1={x(hover)} x2={x(hover)} y1={PAD.top} y2={baseline} stroke="var(--color-line-strong)" />
        )}
        {rev.map(([cx, cy], i) => (
          <circle
            key={i}
            cx={cx}
            cy={cy}
            r={hover === i ? 5 : 3.5}
            fill="white"
            stroke="var(--color-brand)"
            strokeWidth={2}
          />
        ))}
      </svg>

      {active && hover != null && (
        <div
          className="pointer-events-none absolute top-0 z-10 w-40 -translate-x-1/2 rounded-lg border border-line bg-surface px-3 py-2 text-[12px] shadow-lg"
          style={{ left: `clamp(80px, ${(x(hover) / W) * 100}%, calc(100% - 80px))` }}
        >
          <p className="font-semibold text-ink">{active.month}</p>
          <p className="mt-1 flex justify-between text-ink-2">
            Revenue <span className="tnum font-medium text-ink">{formatReporting(active.revenue, BASE) ?? "—"}</span>
          </p>
          {active.target != null && (
            <p className="flex justify-between text-ink-2">
              Target <span className="tnum font-medium text-ink">{formatReporting(active.target, BASE) ?? "—"}</span>
            </p>
          )}
        </div>
      )}
    </div>
  );
}
