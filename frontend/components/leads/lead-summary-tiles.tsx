import { cn } from "@/lib/format";
import type { LeadSummary, LeadView } from "@/lib/leads";

type Count = "total" | "newThisMonth" | "qualified" | "unassigned";
type Change = "totalChange" | "newThisMonthChange";

// The tiles double as quick views. "invert" marks a count where lower is better.
const VIEWS: { id: LeadView; label: string; count: Count; change?: Change; invert?: boolean }[] = [
  { id: "ALL", label: "All leads", count: "total", change: "totalChange" },
  { id: "NEW_THIS_MONTH", label: "New this month", count: "newThisMonth", change: "newThisMonthChange" },
  { id: "QUALIFIED", label: "Qualified", count: "qualified" },
  { id: "UNASSIGNED", label: "Unassigned", count: "unassigned", invert: true },
];

export function LeadSummaryTiles({
  summary,
  view,
  onChange,
}: {
  summary: LeadSummary | undefined;
  view: LeadView;
  onChange: (view: LeadView) => void;
}) {
  return (
    <div role="group" aria-label="Lead views" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {VIEWS.map((v) => {
        const delta = v.change ? summary?.[v.change] : null;
        const good = v.invert ? (delta ?? 0) <= 0 : (delta ?? 0) >= 0;
        return (
          <button
            key={v.id}
            type="button"
            aria-pressed={view === v.id}
            onClick={() => onChange(v.id)}
            className={cn(
              "rounded-xl border bg-surface px-4 py-3.5 text-left transition-colors",
              view === v.id ? "border-brand ring-1 ring-brand" : "border-line hover:border-line-strong",
            )}
          >
            <span className="block truncate text-[12.5px] font-medium text-ink-2">{v.label}</span>
            <span className="mt-2 flex items-baseline gap-2">
              <span className="tnum text-[24px] leading-none font-semibold tracking-[-0.02em] text-ink">
                {summary ? summary[v.count] : "–"}
              </span>
              {delta != null && (
                <span className={cn("tnum text-[12px] font-semibold", good ? "text-up" : "text-down")}>
                  {delta > 0 ? "+" : ""}
                  {delta}%
                </span>
              )}
            </span>
          </button>
        );
      })}
    </div>
  );
}
