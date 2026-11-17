import type { FollowUpSummary } from "@/lib/follow-ups";
import { cn } from "@/lib/format";

export function FollowUpTiles({ summary }: { summary: FollowUpSummary | undefined }) {
  const overdue = summary?.overdue ?? 0;
  const tiles = [
    {
      label: "Today",
      note: `${summary?.todayRemaining ?? 0} remaining`,
      value: summary?.today ?? 0,
      bar: "tone-solid tone-sky",
    },
    { label: "Upcoming", note: "Next 7 days", value: summary?.upcoming ?? 0, bar: "tone-solid tone-violet" },
    {
      label: "Overdue",
      note: overdue ? "Needs attention" : "All caught up",
      value: overdue,
      bar: "tone-solid tone-red",
    },
    { label: "Completed", note: "This month", value: summary?.completedThisMonth ?? 0, bar: "tone-solid tone-green" },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {tiles.map((t) => (
        <div key={t.label} className="flex items-center gap-3 rounded-xl border border-line bg-surface px-4 py-3.5">
          <span className={cn("h-9 w-1 rounded-full", t.bar)} aria-hidden />
          <div className="min-w-0">
            <p className="text-[12.5px] font-medium text-ink-2">{t.label}</p>
            <p className="truncate text-[12px] text-ink-3">{t.note}</p>
          </div>
          <p className="tnum ml-auto text-[24px] leading-none font-semibold tracking-[-0.02em] text-ink">{t.value}</p>
        </div>
      ))}
    </div>
  );
}
