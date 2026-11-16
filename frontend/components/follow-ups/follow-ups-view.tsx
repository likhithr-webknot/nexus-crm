"use client";

import { useState } from "react";
import { CalendarCheck, Check, ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { TODAY, type FollowUp, type FollowUpType } from "@/lib/mock-data";
import { Avatar, Card, NEEDS_API, Page, PageHeader, buttonPrimary } from "@/components/ui/primitives";
import { cn, daysBetween, formatDay, formatLongDay, formatTime } from "@/lib/format";

const TYPE_STYLES: Record<FollowUpType, { bar: string; chip: string }> = {
  Call: { bar: "bg-sky-500", chip: "border-sky-500 bg-sky-50 text-sky-800" },
  Meeting: { bar: "bg-violet-500", chip: "border-violet-500 bg-violet-50 text-violet-800" },
  Email: { bar: "bg-amber-500", chip: "border-amber-500 bg-amber-50 text-amber-900" },
  Task: { bar: "bg-slate-400", chip: "border-slate-400 bg-slate-100 text-slate-700" },
};

const navButton =
  "grid size-8 place-items-center rounded-lg border border-line bg-surface text-ink-2 transition-colors hover:border-line-strong hover:text-ink";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const monthLabel = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" });
const day = (f: FollowUp) => f.dueAt.slice(0, 10);

/** Every date shown for a month, padded to whole weeks. All maths in UTC so the server and browser agree. */
function monthGrid(year: number, month: number) {
  const lead = new Date(Date.UTC(year, month, 1)).getUTCDay();
  const days = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  const cells = Math.ceil((lead + days) / 7) * 7;
  return Array.from({ length: cells }, (_, i) => {
    const date = new Date(Date.UTC(year, month, i - lead + 1));
    return { iso: date.toISOString().slice(0, 10), inMonth: date.getUTCMonth() === month, number: date.getUTCDate() };
  });
}

export function FollowUpsView({ followUps }: { followUps: FollowUp[] }) {
  const [done, setDone] = useState<ReadonlySet<string>>(() => new Set(followUps.filter((f) => f.done).map((f) => f.id)));
  const [selected, setSelected] = useState(TODAY);
  const [cursor, setCursor] = useState(() => ({ year: Number(TODAY.slice(0, 4)), month: Number(TODAY.slice(5, 7)) - 1 }));

  const toggle = (id: string) =>
    setDone((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  const shiftMonth = (by: number) =>
    setCursor(({ year, month }) => {
      const d = new Date(Date.UTC(year, month + by, 1));
      return { year: d.getUTCFullYear(), month: d.getUTCMonth() };
    });
  const goToday = () => {
    setSelected(TODAY);
    setCursor({ year: Number(TODAY.slice(0, 4)), month: Number(TODAY.slice(5, 7)) - 1 });
  };

  const sorted = [...followUps].sort((a, b) => a.dueAt.localeCompare(b.dueAt));
  const open = sorted.filter((f) => !done.has(f.id));
  const today = sorted.filter((f) => day(f) === TODAY);
  const overdue = open.filter((f) => day(f) < TODAY);
  const upcoming = open.filter((f) => day(f) > TODAY && daysBetween(TODAY, day(f)) <= 7);
  const completed = sorted.filter((f) => done.has(f.id) && day(f).startsWith(TODAY.slice(0, 7)));
  const agenda = sorted.filter((f) => day(f) === selected);

  const tiles = [
    { label: "Today", note: `${today.filter((f) => !done.has(f.id)).length} remaining`, value: today.length, bar: "bg-sky-500" },
    { label: "Upcoming", note: "Next 7 days", value: upcoming.length, bar: "bg-violet-500" },
    { label: "Overdue", note: overdue.length ? "Needs attention" : "All caught up", value: overdue.length, bar: "bg-rose-500" },
    { label: "Completed", note: "This month", value: completed.length, bar: "bg-emerald-500" },
  ];

  return (
    <Page>
      <PageHeader title="Follow-ups" subtitle="Stay on top of every customer conversation.">
        <button className={buttonPrimary} disabled title={NEEDS_API}>
          <Plus className="size-4" aria-hidden />
          New follow-up
        </button>
      </PageHeader>

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

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card className="overflow-hidden">
          <div className="flex items-center gap-2 px-4 py-3">
            <h2 className="mr-auto text-[15px] font-semibold tracking-[-0.01em] text-ink">
              {monthLabel.format(new Date(Date.UTC(cursor.year, cursor.month, 1)))}
            </h2>
            <button onClick={goToday} className={cn(navButton, "w-auto px-3 text-[13px] font-medium")}>
              Today
            </button>
            <button onClick={() => shiftMonth(-1)} aria-label="Previous month" className={navButton}>
              <ChevronLeft className="size-4" aria-hidden />
            </button>
            <button onClick={() => shiftMonth(1)} aria-label="Next month" className={navButton}>
              <ChevronRight className="size-4" aria-hidden />
            </button>
          </div>

          <div className="grid grid-cols-7 border-y border-line bg-canvas/60 text-center text-[11.5px] font-medium tracking-wide text-ink-3 uppercase">
            {WEEKDAYS.map((w) => (
              <div key={w} className="py-2">
                {w}
              </div>
            ))}
          </div>

          {/* The 1px gaps show the grid colour underneath, which draws the cell borders. */}
          <div className="grid grid-cols-7 gap-px bg-line">
            {monthGrid(cursor.year, cursor.month).map((cell) => {
              const items = sorted.filter((f) => day(f) === cell.iso);
              const isToday = cell.iso === TODAY;
              return (
                <button
                  key={cell.iso}
                  onClick={() => setSelected(cell.iso)}
                  aria-label={`${formatLongDay(cell.iso)}, ${items.length} follow-ups`}
                  aria-pressed={cell.iso === selected}
                  className={cn(
                    "flex min-h-[68px] flex-col gap-1 p-1.5 text-left transition-colors focus-visible:relative sm:min-h-[104px] sm:p-2",
                    cell.iso === selected ? "bg-brand-soft/70" : cell.inMonth ? "bg-surface hover:bg-canvas/70" : "bg-canvas/60",
                  )}
                >
                  <span
                    className={cn(
                      "tnum grid size-6 place-items-center rounded-full text-[12px] font-medium",
                      isToday ? "bg-brand text-white" : cell.inMonth ? "text-ink" : "text-ink-3",
                    )}
                  >
                    {cell.number}
                  </span>
                  {/* Phones get dots; wider screens get readable chips. */}
                  <span className="flex gap-1 sm:hidden" aria-hidden>
                    {items.slice(0, 4).map((f) => (
                      <span key={f.id} className={cn("size-1.5 rounded-full", done.has(f.id) ? "bg-slate-300" : TYPE_STYLES[f.type].bar)} />
                    ))}
                  </span>
                  <span className="hidden w-full flex-col gap-1 sm:flex" aria-hidden>
                    {items.slice(0, 2).map((f) => (
                      <span
                        key={f.id}
                        className={cn(
                          "truncate rounded border-l-2 px-1.5 py-0.5 text-[11.5px]",
                          done.has(f.id) ? "border-slate-300 bg-slate-50 text-ink-3 line-through" : TYPE_STYLES[f.type].chip,
                        )}
                      >
                        <span className="tnum font-semibold">{formatTime(f.dueAt)}</span> {f.contact.split(" ")[0]}
                      </span>
                    ))}
                    {items.length > 2 && <span className="px-1.5 text-[11.5px] text-ink-3">+{items.length - 2} more</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </Card>

        <Card>
          <header className="border-b border-line px-5 py-4">
            <h2 className="text-[15px] font-semibold tracking-[-0.01em] text-ink">
              {selected === TODAY ? "Today's agenda" : "Agenda"}
            </h2>
            <p className="mt-0.5 text-[13px] text-ink-3">{formatLongDay(selected)}</p>
          </header>
          {agenda.length === 0 ? (
            <div className="flex flex-col items-center px-6 py-10 text-center">
              <CalendarCheck className="size-6 text-ink-3" aria-hidden />
              <p className="mt-2 text-[13px] text-ink-3">Nothing scheduled for this day.</p>
            </div>
          ) : (
            <ul className="divide-y divide-line">
              {agenda.map((f) => (
                <AgendaItem key={f.id} item={f} done={done.has(f.id)} onToggle={toggle} when={formatTime(f.dueAt)} />
              ))}
            </ul>
          )}

          {overdue.length > 0 && (
            <>
              <h3 className="border-y border-line bg-rose-50/60 px-5 py-2 text-[12px] font-semibold tracking-wide text-rose-700 uppercase">
                Overdue · {overdue.length}
              </h3>
              <ul className="divide-y divide-line">
                {overdue.map((f) => (
                  <AgendaItem key={f.id} item={f} done={false} onToggle={toggle} when={formatDay(f.dueAt)} late />
                ))}
              </ul>
            </>
          )}
        </Card>
      </div>
    </Page>
  );
}

function AgendaItem({
  item,
  done,
  onToggle,
  when,
  late,
}: {
  item: FollowUp;
  done: boolean;
  onToggle: (id: string) => void;
  when: string;
  late?: boolean;
}) {
  return (
    <li className="flex gap-3 px-5 py-3.5">
      <span className={cn("tnum w-11 shrink-0 pt-0.5 text-[12px]", late ? "font-medium text-down" : "text-ink-3")}>{when}</span>
      <span className={cn("w-0.5 shrink-0 rounded-full", done ? "bg-slate-200" : TYPE_STYLES[item.type].bar)} aria-hidden />
      <div className="min-w-0 flex-1">
        <p className={cn("truncate text-[13.5px] font-medium", done ? "text-ink-3 line-through" : "text-ink")}>{item.title}</p>
        <p className="truncate text-[12.5px] text-ink-3">
          {item.type} · {item.contact}, {item.company}
        </p>
        <p className="mt-1.5 flex items-center gap-1.5 text-[12px] text-ink-2">
          <Avatar name={item.owner} />
          {item.owner}
        </p>
      </div>
      <button
        role="checkbox"
        aria-checked={done}
        aria-label={`Mark "${item.title}" as done`}
        onClick={() => onToggle(item.id)}
        className={cn(
          "grid size-7 shrink-0 place-items-center rounded-md border transition-colors",
          done ? "border-up bg-up text-white" : "border-line-strong text-ink-3 hover:border-brand hover:text-brand",
        )}
      >
        <Check className="size-4" strokeWidth={2.5} aria-hidden />
      </button>
    </li>
  );
}
