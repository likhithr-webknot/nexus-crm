import type { LeadSource } from "@/lib/mock-data";

const R = 54;
const C = 2 * Math.PI * R;
const GAP = 3; // px gap between segments along the circumference

export function LeadSourcesDonut({ sources, total }: { sources: LeadSource[]; total: string }) {
  const segments = sources.map((s, i) => ({
    ...s,
    len: (s.share / 100) * C,
    start: (sources.slice(0, i).reduce((a, p) => a + p.share, 0) / 100) * C,
  }));
  return (
    <div className="flex flex-col items-center gap-6 sm:flex-row sm:items-center lg:flex-col xl:flex-row">
      <div className="relative size-40 shrink-0">
        <svg viewBox="0 0 140 140" className="size-full -rotate-90" aria-hidden>
          <circle cx={70} cy={70} r={R} fill="none" stroke="var(--color-canvas)" strokeWidth={16} />
          {segments.map((s) => (
              <circle
                key={s.label}
                cx={70}
                cy={70}
                r={R}
                fill="none"
                stroke={s.color}
                strokeWidth={16}
                strokeDasharray={`${Math.max(s.len - GAP, 0)} ${C}`}
                strokeDashoffset={-s.start}
              />
          ))}
        </svg>
        <div className="absolute inset-0 grid place-content-center text-center">
          <p className="tnum text-[22px] leading-none font-semibold tracking-[-0.02em]">{total}</p>
          <p className="mt-1 text-[11px] text-ink-3">Total leads</p>
        </div>
      </div>

      <ul className="w-full space-y-2.5">
        {sources.map((s) => (
          <li key={s.label} className="flex items-center gap-2.5 text-[13px]">
            <span className="size-2.5 shrink-0 rounded-sm" style={{ background: s.color }} aria-hidden />
            <span className="text-ink-2">{s.label}</span>
            <span className="tnum ml-auto font-semibold text-ink">{s.share}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
