"use client";

import { useState } from "react";
import type { RevenuePoint } from "@/lib/mock-data";
import { formatCompactUsd, formatUsd } from "@/lib/format";

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

  const max = Math.ceil(Math.max(...data.flatMap((d) => [d.revenue, d.target])) / 100_000) * 100_000;
  const ticks = Array.from({ length: max / 100_000 + 1 }, (_, i) => i * 100_000);
  const innerW = W - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;
  const x = (i: number) => PAD.left + (i / (data.length - 1)) * innerW;
  const y = (v: number) => PAD.top + innerH - (v / max) * innerH;

  const rev = data.map((d, i) => [x(i), y(d.revenue)] as [number, number]);
  const tgt = data.map((d, i) => [x(i), y(d.target)] as [number, number]);
  const revLine = smoothPath(rev);
  const baseline = PAD.top + innerH;

  const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    const i = Math.round(((px - PAD.left) / innerW) * (data.length - 1));
    setHover(Math.max(0, Math.min(data.length - 1, i)));
  };

  const active = hover != null ? data[hover] : null;

  return (
    <div className="relative">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="h-auto w-full touch-none select-none"
        role="img"
        aria-label={`Monthly revenue vs target, ${data[0].month} to ${data.at(-1)!.month}`}
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
              {formatCompactUsd(t)}
            </text>
          </g>
        ))}
        {data.map((d, i) => (
          <text key={d.month} x={x(i)} y={H - 6} textAnchor="middle" className="fill-ink-3 text-[11px]">
            {d.month}
          </text>
        ))}

        <path d={`${revLine}L${x(data.length - 1)},${baseline}L${x(0)},${baseline}Z`} fill="url(#rev-fill)" />
        <path d={smoothPath(tgt)} fill="none" stroke="#94a3b8" strokeWidth={1.5} strokeDasharray="5 5" />
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
          <p className="font-semibold text-ink">{active.month} 2025</p>
          <p className="mt-1 flex justify-between text-ink-2">
            Revenue <span className="tnum font-medium text-ink">{formatUsd(active.revenue)}</span>
          </p>
          <p className="flex justify-between text-ink-2">
            Target <span className="tnum font-medium text-ink">{formatUsd(active.target)}</span>
          </p>
        </div>
      )}
    </div>
  );
}
