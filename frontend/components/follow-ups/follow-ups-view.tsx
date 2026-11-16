"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarCheck, Check, ChevronLeft, ChevronRight, Plus, WifiOff } from "lucide-react";
import { Avatar, Card, EmptyState, Field, Page, PageHeader, buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { cn, formatDay, formatLongDay, formatTime, localIso, readForm, todayLocal, toInstant } from "@/lib/format";
import { errorMessage } from "@/lib/api";
import { listContacts } from "@/lib/contacts";
import {
  FOLLOW_UP_TYPE_LABELS,
  createFollowUp,
  followUpSummary,
  listFollowUps,
  setFollowUpDone,
  type FollowUp,
  type FollowUpType,
} from "@/lib/follow-ups";
import { listAssignable } from "@/lib/users";
import { useSession } from "@/components/auth/session";

type Item = FollowUp & { local: string; done: boolean };

const withLocal = (f: FollowUp): Item => ({ ...f, local: localIso(f.dueAt), done: f.status === "DONE" });

const TYPE_STYLES: Record<FollowUpType, { bar: string; chip: string }> = {
  CALL: { bar: "bg-sky-500", chip: "border-sky-500 bg-sky-50 text-sky-800" },
  MEETING: { bar: "bg-violet-500", chip: "border-violet-500 bg-violet-50 text-violet-800" },
  EMAIL: { bar: "bg-amber-500", chip: "border-amber-500 bg-amber-50 text-amber-900" },
  TASK: { bar: "bg-slate-400", chip: "border-slate-400 bg-slate-100 text-slate-700" },
};

const navButton =
  "grid size-8 place-items-center rounded-lg border border-line bg-surface text-ink-2 transition-colors hover:border-line-strong hover:text-ink";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const monthLabel = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" });
const day = (f: Item) => f.local.slice(0, 10);

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

const addDays = (iso: string, by: number) => new Date(Date.parse(iso) + by * 86_400_000).toISOString().slice(0, 10);

export function FollowUpsView() {
  const queryClient = useQueryClient();
  const TODAY = todayLocal();
  const [adding, setAdding] = useState(false);
  const [selected, setSelected] = useState(TODAY);
  const [cursor, setCursor] = useState(() => ({ year: Number(TODAY.slice(0, 4)), month: Number(TODAY.slice(5, 7)) - 1 }));

  const shiftMonth = (by: number) =>
    setCursor(({ year, month }) => {
      const d = new Date(Date.UTC(year, month + by, 1));
      return { year: d.getUTCFullYear(), month: d.getUTCMonth() };
    });
  const goToday = () => {
    setSelected(TODAY);
    setCursor({ year: Number(TODAY.slice(0, 4)), month: Number(TODAY.slice(5, 7)) - 1 });
  };

  // The calendar loads every day it shows, including the greyed days around the month.
  const grid = monthGrid(cursor.year, cursor.month);
  const gridFrom = toInstant(grid[0].iso);
  const gridTo = toInstant(addDays(grid[grid.length - 1].iso, 1));
  const calendar = useQuery({
    queryKey: ["follow-ups", "calendar", gridFrom, gridTo],
    queryFn: () => listFollowUps({ from: gridFrom, to: gridTo, size: 500 }),
  });
  const overdueQuery = useQuery({
    queryKey: ["follow-ups", "overdue", TODAY],
    queryFn: () => listFollowUps({ status: "OPEN", to: toInstant(TODAY), size: 200 }),
  });
  const summary = useQuery({
    queryKey: ["follow-ups", "summary", TODAY],
    queryFn: () => followUpSummary(toInstant(TODAY), toInstant(addDays(TODAY, 1)), toInstant(`${TODAY.slice(0, 7)}-01`)),
  });

  const toggle = useMutation({
    mutationFn: (f: Item) => setFollowUpDone(f.id, !f.done),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["follow-ups"] });
      void queryClient.invalidateQueries({ queryKey: ["dashboard-followups"] });
    },
    onError: (e) => toast(errorMessage(e, "Couldn't update the follow-up.")),
  });

  const sorted = (calendar.data?.items ?? []).map(withLocal).sort((a, b) => a.local.localeCompare(b.local));
  const overdue = (overdueQuery.data?.items ?? []).map(withLocal).sort((a, b) => a.local.localeCompare(b.local));
  const agenda = sorted.filter((f) => day(f) === selected && f.status !== "CANCELLED");
  const monthLabelText = monthLabel.format(new Date(Date.UTC(cursor.year, cursor.month, 1)));

  const tiles = [
    { label: "Today", note: `${summary.data?.todayRemaining ?? 0} remaining`, value: summary.data?.today ?? 0, bar: "bg-sky-500" },
    { label: "Upcoming", note: "Next 7 days", value: summary.data?.upcoming ?? 0, bar: "bg-violet-500" },
    {
      label: "Overdue",
      note: (summary.data?.overdue ?? 0) ? "Needs attention" : "All caught up",
      value: summary.data?.overdue ?? 0,
      bar: "bg-rose-500",
    },
    { label: "Completed", note: "This month", value: summary.data?.completedThisMonth ?? 0, bar: "bg-emerald-500" },
  ];

  if (calendar.isError && !calendar.data) {
    return (
      <Page>
        <PageHeader title="Follow-ups" subtitle="Stay on top of every customer conversation." />
        <Card>
          <EmptyState icon={WifiOff} title="Couldn't load follow-ups" body="Check your connection, then try again.">
            <button onClick={() => calendar.refetch()} className={buttonSecondary}>
              Try again
            </button>
          </EmptyState>
        </Card>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader title="Follow-ups" subtitle="Stay on top of every customer conversation.">
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
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
              {monthLabelText}
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
            {grid.map((cell) => {
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
                      <span key={f.id} className={cn("size-1.5 rounded-full", f.done ? "bg-slate-300" : TYPE_STYLES[f.type].bar)} />
                    ))}
                  </span>
                  <span className="hidden w-full flex-col gap-1 sm:flex" aria-hidden>
                    {items.slice(0, 2).map((f) => (
                      <span
                        key={f.id}
                        className={cn(
                          "truncate rounded border-l-2 px-1.5 py-0.5 text-[11.5px]",
                          f.done ? "border-slate-300 bg-slate-50 text-ink-3 line-through" : TYPE_STYLES[f.type].chip,
                        )}
                      >
                        <span className="tnum font-semibold">{formatTime(f.local)}</span> {(f.about?.name ?? f.title).split(" ")[0]}
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
                <AgendaItem key={f.id} item={f} onToggle={(item) => toggle.mutate(item)} when={formatTime(f.local)} />
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
                  <AgendaItem key={f.id} item={f} onToggle={(item) => toggle.mutate(item)} when={formatDay(f.local)} late />
                ))}
              </ul>
            </>
          )}
        </Card>
      </div>

      <NewFollowUpDialog
        open={adding}
        onClose={() => setAdding(false)}
        defaultDay={selected}
        onAdded={(followUp) => {
          // Show the day it landed on, so the new entry is visible straight away.
          const local = localIso(followUp.dueAt);
          setSelected(local.slice(0, 10));
          setCursor({ year: Number(local.slice(0, 4)), month: Number(local.slice(5, 7)) - 1 });
          toast(`Follow-up scheduled for ${formatDay(local)}.`);
        }}
      />
    </Page>
  );
}

function AgendaItem({
  item,
  onToggle,
  when,
  late,
}: {
  item: Item;
  onToggle: (item: Item) => void;
  when: string;
  late?: boolean;
}) {
  const done = item.done;
  return (
    <li className="flex gap-3 px-5 py-3.5">
      <span className={cn("tnum w-11 shrink-0 pt-0.5 text-[12px]", late ? "font-medium text-down" : "text-ink-3")}>{when}</span>
      <span className={cn("w-0.5 shrink-0 rounded-full", done ? "bg-slate-200" : TYPE_STYLES[item.type].bar)} aria-hidden />
      <div className="min-w-0 flex-1">
        <p className={cn("truncate text-[13.5px] font-medium", done ? "text-ink-3 line-through" : "text-ink")}>{item.title}</p>
        <p className="truncate text-[12.5px] text-ink-3">
          {FOLLOW_UP_TYPE_LABELS[item.type]}
          {item.about ? ` · ${item.about.name}${item.about.company ? `, ${item.about.company}` : ""}` : ""}
        </p>
        <p className="mt-1.5 flex items-center gap-1.5 text-[12px] text-ink-2">
          <Avatar name={item.assigneeName ?? "?"} />
          {item.assigneeName}
        </p>
      </div>
      <button
        role="checkbox"
        aria-checked={done}
        aria-label={`Mark "${item.title}" as done`}
        onClick={() => onToggle(item)}
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

function NewFollowUpDialog({
  open,
  onClose,
  defaultDay,
  onAdded,
}: {
  open: boolean;
  onClose: () => void;
  defaultDay: string;
  onAdded: (followUp: FollowUp) => void;
}) {
  const queryClient = useQueryClient();
  const session = useSession();
  const [error, setError] = useState<string | null>(null);
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable, enabled: open });
  const contacts = useQuery({ queryKey: ["contacts", ""], queryFn: () => listContacts({ size: 100 }), enabled: open });

  const add = useMutation({
    mutationFn: createFollowUp,
    onSuccess: (followUp) => {
      void queryClient.invalidateQueries({ queryKey: ["follow-ups"] });
      void queryClient.invalidateQueries({ queryKey: ["dashboard-followups"] });
      onAdded(followUp);
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    setError(null);
    add.mutate({
      title: f.title,
      type: f.type as FollowUpType,
      contactId: f.contact,
      assigneeId: f.owner || undefined,
      dueAt: toInstant(f.date, f.time),
    });
  };

  return (
    <Dialog open={open} onClose={onClose} title="New follow-up" description="A reminder to get back to someone.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="What needs doing" className="sm:col-span-2">
            <input name="title" required maxLength={160} autoComplete="off" placeholder="Send revised proposal" className={fieldControl} />
          </Field>
          <Field label="Type">
            <select name="type" className={fieldControl}>
              {(Object.keys(TYPE_STYLES) as FollowUpType[]).map((t) => (
                <option key={t} value={t}>
                  {FOLLOW_UP_TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Contact">
            <select name="contact" required defaultValue="" className={fieldControl}>
              <option value="" disabled>
                Choose a contact
              </option>
              {contacts.data?.items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.company ? `, ${c.company.name}` : ""}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Date">
            <input name="date" type="date" required defaultValue={defaultDay} className={fieldControl} />
          </Field>
          <Field label="Time">
            <input name="time" type="time" required defaultValue="09:00" className={fieldControl} />
          </Field>
          <Field label="Assigned to" className="sm:col-span-2">
            <select name="owner" defaultValue={session.id} className={fieldControl}>
              {(people.data ?? [{ id: session.id, displayName: session.displayName }]).map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
          {error && (
            <div className="sm:col-span-2">
              <FormError>{error}</FormError>
            </div>
          )}
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Schedule" pending={add.isPending} />
      </form>
    </Dialog>
  );
}
