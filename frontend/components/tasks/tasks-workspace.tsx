"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Check, Circle, Plus, Search, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useSession } from "@/components/auth/session";
import { NewFollowUpDialog } from "@/components/follow-ups/new-follow-up-dialog";
import { EditFollowUpDialog } from "@/components/follow-ups/edit-follow-up-dialog";
import { withLocal, type Item } from "@/components/follow-ups/follow-up-item";
import {
  Card,
  EmptyState,
  Page,
  PageHeader,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api";
import { FOLLOW_UP_TYPE_LABELS, listFollowUps, setFollowUpDone, type FollowUpType } from "@/lib/follow-ups";
import { formatDay, todayLocal } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";

type View = "My tasks" | "Team tasks" | "Today" | "Upcoming" | "Overdue" | "Completed";
const VIEWS: View[] = ["My tasks", "Team tasks", "Today", "Upcoming", "Overdue", "Completed"];

export function TasksWorkspace() {
  const user = useSession();
  const [view, setView] = useState<View>("My tasks");
  const [query, setQuery] = useState("");
  const [type, setType] = useState<FollowUpType | "">("");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [pendingIds, setPendingIds] = useState<string[]>([]);
  const client = useQueryClient();
  const today = todayLocal();
  const tasks = useQuery({
    queryKey: [...KEYS.followUps, "tasks", view],
    queryFn: () =>
      listFollowUps({
        status: view === "Completed" ? "DONE" : "OPEN",
        assignee: view === "My tasks" ? "me" : undefined,
        size: 200,
      }),
  });
  const done = useMutation({
    mutationFn: ({ id, complete }: { id: string; complete: boolean }) => setFollowUpDone(id, complete),
    onSuccess: (_, vars) => {
      setPendingIds((ids) => ids.filter((id) => id !== vars.id));
      void client.invalidateQueries({ queryKey: KEYS.followUps });
      toast(
        vars.complete ? "Task completed." : "Task reopened.",
        vars.complete ? { label: "Undo", run: () => done.mutate({ id: vars.id, complete: false }) } : undefined,
      );
    },
    onError: (error, vars) => {
      setPendingIds((ids) => ids.filter((id) => id !== vars.id));
      toast(errorMessage(error, "Task could not be updated."));
    },
  });
  const items = (tasks.data?.items ?? [])
    .map(withLocal)
    .filter((item) => {
      if (pendingIds.includes(item.id)) return false;
      if (type && item.type !== type) return false;
      if (view === "My tasks" && item.assigneeId !== user.id) return false;
      if (view === "Today" && item.local.slice(0, 10) !== today) return false;
      if (view === "Upcoming" && item.local.slice(0, 10) <= today) return false;
      if (view === "Overdue" && item.local.slice(0, 10) >= today) return false;
      return !query || `${item.title} ${item.about?.name ?? ""}`.toLowerCase().includes(query.toLowerCase());
    })
    .sort((a, b) => a.local.localeCompare(b.local));

  return (
    <Page>
      <PageHeader title="Tasks" subtitle="A focused queue for every customer commitment.">
        <Link href="/calendar" className={buttonSecondary}>
          <CalendarDays className="size-4" />
          Calendar
        </Link>
        <button onClick={() => setAdding(true)} className={buttonPrimary}>
          <Plus className="size-4" />
          New task
        </button>
      </PageHeader>
      <Card className="overflow-hidden">
        <div
          className="flex gap-1 overflow-x-auto border-b border-line px-3 pt-3"
          role="tablist"
          aria-label="Task views"
        >
          {VIEWS.filter((item) => item !== "Team tasks" || user.role !== "REP").map((item) => (
            <button
              key={item}
              role="tab"
              aria-selected={view === item}
              onClick={() => setView(item)}
              className={`shrink-0 border-b-2 px-3 pb-3 text-[12.5px] font-medium ${view === item ? "border-brand text-brand" : "border-transparent text-ink-2 hover:text-ink"}`}
            >
              {item}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2 border-b border-line p-3">
          <div className="relative min-w-48 flex-1 sm:max-w-sm">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search tasks"
              aria-label="Search tasks"
              className="h-9 w-full rounded-lg border border-line bg-canvas pr-3 pl-9 text-[13px] focus:border-brand focus:outline-none"
            />
          </div>
          <label className="relative">
            <SlidersHorizontal className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-ink-3" />
            <span className="sr-only">Filter by activity type</span>
            <select
              className={`${control} pr-3 pl-8`}
              value={type}
              onChange={(event) => setType(event.target.value as FollowUpType | "")}
            >
              <option value="">All types</option>
              {Object.entries(FOLLOW_UP_TYPE_LABELS).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {tasks.isError ? (
          <p className="p-8 text-[13px] text-down">
            Tasks could not be loaded.{" "}
            <button onClick={() => tasks.refetch()} className="underline">
              Retry
            </button>
          </p>
        ) : tasks.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading tasks">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-12 rounded-md skeleton" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            icon={Check}
            title="Nothing in this view"
            body={
              query || type
                ? "Try clearing the search or type filter."
                : "New tasks will appear here as work is scheduled."
            }
          >
            {query || type ? (
              <button
                onClick={() => {
                  setQuery("");
                  setType("");
                }}
                className={buttonSecondary}
              >
                Clear filters
              </button>
            ) : (
              <button onClick={() => setAdding(true)} className={buttonPrimary}>
                Create task
              </button>
            )}
          </EmptyState>
        ) : (
          <ul className="divide-y divide-line">
            {items.map((item) => (
              <li key={item.id} className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-canvas">
                <button
                  onClick={() => {
                    setPendingIds((ids) => [...ids, item.id]);
                    done.mutate({ id: item.id, complete: !item.done });
                  }}
                  aria-label={`${item.done ? "Reopen" : "Complete"} ${item.title}`}
                  className="grid size-8 shrink-0 place-items-center rounded-md text-ink-3 hover:text-brand"
                >
                  {item.done ? <Check className="size-5 text-up" /> : <Circle className="size-5" />}
                </button>
                <button onClick={() => setEditing(item)} className="min-w-0 flex-1 text-left">
                  <span
                    className={`block truncate text-[13px] font-medium ${item.done ? "text-ink-3 line-through" : "text-ink"}`}
                  >
                    {item.title}
                  </span>
                  <span className="mt-0.5 block truncate text-[11px] text-ink-3">
                    {FOLLOW_UP_TYPE_LABELS[item.type]} · {item.about?.name ?? "No linked record"} ·{" "}
                    {item.assigneeName ?? "Unassigned"}
                  </span>
                </button>
                <time
                  className={`tnum shrink-0 text-[11px] ${item.local.slice(0, 10) < today && !item.done ? "text-down" : "text-ink-3"}`}
                >
                  {formatDay(item.local)}
                </time>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <NewFollowUpDialog
        open={adding}
        onClose={() => setAdding(false)}
        defaultDay={today}
        onAdded={() => void client.invalidateQueries({ queryKey: KEYS.followUps })}
      />
      <EditFollowUpDialog item={editing} onClose={() => setEditing(null)} />
    </Page>
  );
}
