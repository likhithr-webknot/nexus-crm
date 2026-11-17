"use client";

import { DeleteDialog } from "@/components/ui/delete-dialog";
import { LoadFailed } from "@/components/ui/load-state";
import { Card, Page, PageHeader, buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { monthGrid, monthOf, shiftMonth } from "@/lib/calendar";
import { deleteFollowUp } from "@/lib/follow-ups";
import { formatDay, formatLongDay, formatTime, localIso, todayLocal } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { addDays, format, parseISO, startOfWeek } from "date-fns";
import { useState } from "react";
import { EditFollowUpDialog } from "./edit-follow-up-dialog";
import { FollowUpAgenda } from "./follow-up-agenda";
import { FollowUpCalendar } from "./follow-up-calendar";
import { dayOf, type Item } from "./follow-up-item";
import { FollowUpTiles } from "./follow-up-tiles";
import { NewFollowUpDialog } from "./new-follow-up-dialog";
import { useFollowUps, useToggleFollowUp } from "./use-follow-ups";

/** The follow-ups page: a calendar, the agenda of the picked day, and the counts above them. */
export function FollowUpsView({ title = "Follow-ups" }: { title?: string }) {
  const today = todayLocal();
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [deleting, setDeleting] = useState<Item | null>(null);
  const [selected, setSelected] = useState(today);
  const [month, setMonth] = useState(() => monthOf(today));
  const [view, setView] = useState<"Month" | "Week" | "Day" | "Agenda">("Month");

  const grid = monthGrid(month);
  const data = useFollowUps(grid, today);
  const toggle = useToggleFollowUp();
  const refresh = useRefresh();

  const showDay = (iso: string) => {
    setSelected(iso);
    setMonth(monthOf(iso));
  };
  const shift = (by: number) => {
    if (view === "Month") {
      setMonth((m) => shiftMonth(m, by));
      return;
    }
    showDay(format(addDays(parseISO(selected), by * (view === "Week" ? 7 : 1)), "yyyy-MM-dd"));
  };
  const weekStart = startOfWeek(parseISO(selected), { weekStartsOn: 1 });
  const weekDays = Array.from({ length: 7 }, (_, index) => format(addDays(weekStart, index), "yyyy-MM-dd"));
  const agendaItems = [...data.items]
    .filter((item) => item.status !== "CANCELLED")
    .sort((a, b) => a.local.localeCompare(b.local));

  if (data.calendar.isError && !data.calendar.data) {
    return (
      <Page>
        <PageHeader title={title} subtitle="Stay on top of every customer conversation." />
        <Card>
          <LoadFailed what="follow-ups" onRetry={() => data.calendar.refetch()} />
        </Card>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader title={title} subtitle="Stay on top of every customer conversation.">
        <div className="flex rounded-lg border border-line bg-surface p-0.5" role="group" aria-label="Calendar view">
          {(["Month", "Week", "Day", "Agenda"] as const).map((mode) => (
            <button
              key={mode}
              onClick={() => setView(mode)}
              aria-pressed={view === mode}
              className={`rounded-md px-2.5 py-1.5 text-[12px] font-medium transition-colors ${view === mode ? "bg-brand-soft text-brand" : "text-ink-2 hover:bg-canvas"}`}
            >
              {mode}
            </button>
          ))}
        </div>
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          New follow-up
        </button>
      </PageHeader>

      <FollowUpTiles summary={data.summary} />

      {view === "Month" && (
        <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
          <FollowUpCalendar
            month={month}
            grid={grid}
            items={data.items}
            today={today}
            selected={selected}
            onSelect={showDay}
            onToday={() => showDay(today)}
            onShift={shift}
          />
          <FollowUpAgenda
            day={selected}
            isToday={selected === today}
            items={data.items.filter((f) => dayOf(f) === selected && f.status !== "CANCELLED")}
            overdue={data.overdue}
            onToggle={(item) => toggle.mutate(item)}
            onEdit={setEditing}
            onDelete={setDeleting}
          />
        </div>
      )}
      {view !== "Month" && (
        <div className="space-y-5">
          <Card className="flex flex-wrap items-center gap-2 px-4 py-3">
            <h2 className="mr-auto text-[15px] font-semibold">
              {view === "Week" ? `${formatDay(weekDays[0])} – ${formatDay(weekDays[6])}` : formatLongDay(selected)}
            </h2>
            <button onClick={() => showDay(today)} className={buttonSecondary}>
              Today
            </button>
            <button onClick={() => shift(-1)} className={buttonSecondary} aria-label={`Previous ${view.toLowerCase()}`}>
              <ChevronLeft className="size-4" />
            </button>
            <button onClick={() => shift(1)} className={buttonSecondary} aria-label={`Next ${view.toLowerCase()}`}>
              <ChevronRight className="size-4" />
            </button>
          </Card>
          {view === "Week" && (
            <Card className="overflow-x-auto">
              <div className="grid min-w-[700px] grid-cols-7 gap-px bg-line">
                {weekDays.map((day) => {
                  const entries = data.items.filter((item) => dayOf(item) === day);
                  return (
                    <button
                      key={day}
                      onClick={() => showDay(day)}
                      aria-pressed={selected === day}
                      className={`min-h-40 bg-surface p-3 text-left align-top hover:bg-canvas ${selected === day ? "ring-2 ring-inset ring-brand" : ""}`}
                    >
                      <span className="block text-[11px] font-medium text-ink-3">{format(parseISO(day), "EEE")}</span>
                      <span className="tnum mt-1 block text-[16px] font-semibold">{format(parseISO(day), "d")}</span>
                      <span className="mt-3 block space-y-1">
                        {entries.slice(0, 3).map((item) => (
                          <span
                            key={item.id}
                            className="block truncate rounded border-l-2 border-brand bg-brand-soft px-1.5 py-1 text-[11px] text-brand-ink"
                          >
                            {formatTime(item.local)} {item.title}
                          </span>
                        ))}
                        {entries.length > 3 && (
                          <span className="block text-[11px] text-ink-3">+{entries.length - 3} more</span>
                        )}
                      </span>
                    </button>
                  );
                })}
              </div>
            </Card>
          )}
          {view === "Agenda" ? (
            <Card>
              <div className="border-b border-line px-5 py-4">
                <h2 className="text-[15px] font-semibold">Upcoming agenda</h2>
                <p className="mt-1 text-[12px] text-ink-3">All scheduled items in the visible calendar range.</p>
              </div>
              {agendaItems.length ? (
                <ul className="divide-y divide-line">
                  {agendaItems.map((item) => (
                    <li key={item.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
                      <time dateTime={item.dueAt} className="tnum w-28 shrink-0 text-[12px] text-ink-3">
                        {formatDay(item.local)} · {formatTime(item.local)}
                      </time>
                      <button
                        onClick={() => setEditing(item)}
                        className="min-w-0 flex-1 truncate text-left text-[13px] font-medium hover:text-brand"
                      >
                        {item.title}
                      </button>
                      <span className="text-[11px] text-ink-3">{item.assigneeName}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="p-5 text-[13px] text-ink-3">No items scheduled in this range.</p>
              )}
            </Card>
          ) : (
            <FollowUpAgenda
              day={selected}
              isToday={selected === today}
              items={data.items.filter((item) => dayOf(item) === selected && item.status !== "CANCELLED")}
              overdue={data.overdue}
              onToggle={(item) => toggle.mutate(item)}
              onEdit={setEditing}
              onDelete={setDeleting}
            />
          )}
        </div>
      )}

      <EditFollowUpDialog item={editing} onClose={() => setEditing(null)} />
      <DeleteDialog
        target={deleting}
        title={(f) => `Delete "${f.title}"?`}
        body="The follow-up is removed from the calendar."
        successMessage={(f) => `${f.title} deleted.`}
        action={(f) => deleteFollowUp(f.id)}
        onClose={() => setDeleting(null)}
        onDeleted={() => void refresh(AFTER.followUp)}
      />
      <NewFollowUpDialog
        open={adding}
        onClose={() => setAdding(false)}
        defaultDay={selected}
        onAdded={(followUp) => {
          // Show the day it landed on, so the new entry is visible straight away.
          const local = localIso(followUp.dueAt);
          showDay(local.slice(0, 10));
          toast(`Follow-up scheduled for ${formatDay(local)}.`);
        }}
      />
    </Page>
  );
}
