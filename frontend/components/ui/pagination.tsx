import { cn } from "@/lib/format";
import { ChevronLeft, ChevronRight } from "lucide-react";

/** Page buttons: first, last and the pages around the current one, with "…" for a gap. */
export function Pagination({ current, pageCount, onChange }: { current: number; pageCount: number; onChange: (page: number) => void }) {
  const pages = [...new Set([1, current - 1, current, current + 1, pageCount])]
    .filter((p) => p >= 1 && p <= pageCount)
    .sort((a, b) => a - b);
  const cell = "tnum grid h-8 min-w-8 place-items-center rounded-md border px-2 text-[12.5px] font-medium";
  const idle = "border-line text-ink-2 hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-40";

  return (
    <nav aria-label="Pagination" className="flex items-center gap-1">
      <button aria-label="Previous page" disabled={current === 1} onClick={() => onChange(current - 1)} className={cn(cell, idle)}>
        <ChevronLeft className="size-4" aria-hidden />
      </button>
      {pages.map((p, i) => (
        <span key={p} className="flex items-center gap-1">
          {i > 0 && p - pages[i - 1] > 1 && <span className="px-1 text-ink-3">…</span>}
          <button
            aria-label={`Page ${p}`}
            aria-current={p === current ? "page" : undefined}
            onClick={() => onChange(p)}
            className={cn(cell, p === current ? "border-brand bg-brand-soft text-brand-ink" : idle)}
          >
            {p}
          </button>
        </span>
      ))}
      <button aria-label="Next page" disabled={current === pageCount} onClick={() => onChange(current + 1)} className={cn(cell, idle)}>
        <ChevronRight className="size-4" aria-hidden />
      </button>
    </nav>
  );
}
