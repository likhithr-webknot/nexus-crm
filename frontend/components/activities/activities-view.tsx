"use client";

import { useState } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Inbox, Mail, Phone, Plus, StickyNote, WifiOff, Zap, type LucideIcon } from "lucide-react";
import {
  Avatar,
  Card,
  EmptyState,
  Field,
  Page,
  PageHeader,
  buttonPrimary,
  buttonSecondary,
  control,
  fieldControl,
} from "@/components/ui/primitives";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { cn, daysBetween, formatLongDay, formatTime, localIso, readForm, todayLocal } from "@/lib/format";
import { errorMessage } from "@/lib/api";
import { activityCounts, listActivities, logActivity, type Activity, type ActivityType } from "@/lib/activities";
import { listContacts } from "@/lib/contacts";
import { listAssignable } from "@/lib/users";

const TYPES: Record<ActivityType, { icon: LucideIcon; tone: string; plural: string; label: string }> = {
  CALL: { icon: Phone, tone: "bg-sky-50 text-sky-700 ring-sky-200", plural: "Calls", label: "Call" },
  EMAIL_LOG: { icon: Mail, tone: "bg-amber-50 text-amber-800 ring-amber-200", plural: "Emails", label: "Email" },
  MEETING: { icon: CalendarDays, tone: "bg-violet-50 text-violet-700 ring-violet-200", plural: "Meetings", label: "Meeting" },
  NOTE: { icon: StickyNote, tone: "bg-emerald-50 text-emerald-700 ring-emerald-200", plural: "Notes", label: "Note" },
  SYSTEM: { icon: Zap, tone: "bg-slate-100 text-slate-600 ring-slate-200", plural: "System", label: "System" },
};

function dayHeading(iso: string) {
  const ago = daysBetween(iso, todayLocal());
  return ago === 0 ? "Today" : ago === 1 ? "Yesterday" : formatLongDay(iso);
}

export function ActivitiesView() {
  const [adding, setAdding] = useState(false);
  const [type, setType] = useState<ActivityType | "">("");
  const [actor, setActor] = useState("");

  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable });
  const counts = useQuery({ queryKey: ["activities", "counts", actor], queryFn: () => activityCounts(actor || undefined) });
  const feed = useInfiniteQuery({
    queryKey: ["activities", "feed", type, actor],
    queryFn: ({ pageParam }) => listActivities({ type, actorId: actor || undefined, page: pageParam, size: 50 }),
    initialPageParam: 1,
    getNextPageParam: (last) => (last.page * last.size < last.total ? last.page + 1 : undefined),
  });
  // Activities arrive newest first, so each day's entries are already together.
  const shown: (Activity & { local: string })[] = (feed.data?.pages.flatMap((p) => p.items) ?? []).map((a) => ({
    ...a,
    local: localIso(a.occurredAt),
  }));
  const days = [...new Set(shown.map((a) => a.local.slice(0, 10)))];
  const countFor = (t: ActivityType | "") =>
    t ? (counts.data?.[t] ?? 0) : Object.values(counts.data ?? {}).reduce((sum, n) => sum + (n ?? 0), 0);
  const clear = () => {
    setType("");
    setActor("");
  };

  return (
    <Page>
      <PageHeader title="Activities" subtitle="Every call, email, meeting and change across your team.">
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          Log activity
        </button>
      </PageHeader>

      <Card>
        <div className="flex flex-wrap items-center gap-2 p-4">
          <div role="group" aria-label="Activity type" className="flex flex-wrap gap-1.5">
            {(["", ...Object.keys(TYPES)] as Array<ActivityType | "">).map((t) => (
              <button
                key={t || "all"}
                aria-pressed={type === t}
                onClick={() => setType(t)}
                className={cn(
                  "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12.5px] font-medium transition-colors",
                  type === t
                    ? "border-brand bg-brand-soft text-brand-ink"
                    : "border-line text-ink-2 hover:border-line-strong hover:text-ink",
                )}
              >
                {t ? TYPES[t].plural : "All"}
                <span className="tnum text-ink-3">{countFor(t)}</span>
              </button>
            ))}
          </div>
          <select
            aria-label="Team member"
            value={actor}
            onChange={(e) => setActor(e.target.value)}
            className={cn(control, "px-2.5 sm:ml-auto")}
          >
            <option value="">Everyone</option>
            {(people.data ?? []).map((u) => (
              <option key={u.id} value={u.id}>
                {u.displayName}
              </option>
            ))}
          </select>
        </div>

        {feed.isError && !feed.data ? (
          <div className="border-t border-line">
            <EmptyState icon={WifiOff} title="Couldn't load activity" body="Check your connection, then try again.">
              <button onClick={() => feed.refetch()} className={buttonSecondary}>
                Try again
              </button>
            </EmptyState>
          </div>
        ) : feed.isPending ? (
          <p className="border-t border-line px-5 py-10 text-center text-[13px] text-ink-3" role="status">
            Loading activity…
          </p>
        ) : shown.length === 0 ? (
          <div className="border-t border-line">
            <EmptyState icon={Inbox} title="No activity matches" body="Nothing has been logged for this combination of filters.">
              <button onClick={clear} className={buttonSecondary}>
                Clear filters
              </button>
            </EmptyState>
          </div>
        ) : (
          days.map((d) => (
            <section key={d} aria-label={dayHeading(d)}>
              <h2 className="border-y border-line bg-canvas/60 px-5 py-2 text-[12px] font-semibold tracking-wide text-ink-3 uppercase">
                {dayHeading(d)}
              </h2>
              <ol className="px-5 py-2">
                {shown
                  .filter((a) => a.local.startsWith(d))
                  .map((a, i, list) => {
                    const { icon: Icon, tone } = TYPES[a.type];
                    return (
                      <li key={a.id} className="relative flex gap-3.5 py-3">
                        {/* Line joining this entry to the next one in the same day. */}
                        {i < list.length - 1 && <span className="absolute top-12 bottom-0 left-[17px] w-px bg-line" aria-hidden />}
                        <span className={cn("grid size-9 shrink-0 place-items-center rounded-full ring-1 ring-inset", tone)}>
                          <Icon className="size-4" aria-hidden />
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="flex flex-wrap items-baseline gap-x-2">
                            <span className="text-[13.5px] font-medium text-ink">{a.subject}</span>
                            {a.about && (
                              <span className="text-[12.5px] text-ink-3">
                                {a.about.name}
                                {a.about.company ? ` · ${a.about.company}` : ""}
                              </span>
                            )}
                          </p>
                          {a.body && <p className="mt-0.5 max-w-prose text-[13px] text-ink-2">{a.body}</p>}
                          <p className="mt-1.5 flex items-center gap-1.5 text-[12px] text-ink-3">
                            {a.actorName ? (
                              <>
                                <Avatar name={a.actorName} />
                                {a.actorName}
                              </>
                            ) : (
                              "Automatic"
                            )}
                          </p>
                        </div>
                        <time dateTime={a.occurredAt} className="tnum shrink-0 pt-0.5 text-[12px] text-ink-3">
                          {formatTime(a.local)}
                        </time>
                      </li>
                    );
                  })}
              </ol>
            </section>
          ))
        )}
        {feed.hasNextPage && (
          <div className="border-t border-line p-3 text-center">
            <button onClick={() => feed.fetchNextPage()} disabled={feed.isFetchingNextPage} className={buttonSecondary}>
              {feed.isFetchingNextPage ? "Loading…" : "Load older activity"}
            </button>
          </div>
        )}
      </Card>

      <LogActivityDialog open={adding} onClose={() => setAdding(false)} onLogged={clear} />
    </Page>
  );
}

function LogActivityDialog({ open, onClose, onLogged }: { open: boolean; onClose: () => void; onLogged: () => void }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const contacts = useQuery({ queryKey: ["contacts", ""], queryFn: () => listContacts({ size: 100 }), enabled: open });

  const log = useMutation({
    mutationFn: logActivity,
    onSuccess: (activity) => {
      toast(`${TYPES[activity.type].label} logged.`);
      void queryClient.invalidateQueries({ queryKey: ["activities"] });
      onLogged();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    setError(null);
    log.mutate({ type: f.type as ActivityType, subject: f.title, body: f.detail, contactId: f.contact });
  };

  return (
    <Dialog open={open} onClose={onClose} title="Log activity" description="Record something that happened with a contact.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Type">
            <select name="type" className={fieldControl}>
              {/* System entries are written by the app, never by hand. */}
              {(Object.keys(TYPES) as ActivityType[])
                .filter((t) => t !== "SYSTEM")
                .map((t) => (
                  <option key={t} value={t}>
                    {TYPES[t].label}
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
          <Field label="Summary" className="sm:col-span-2">
            <input name="title" required maxLength={160} autoComplete="off" placeholder="Pricing call" className={fieldControl} />
          </Field>
          <Field label="Notes" className="sm:col-span-2">
            <textarea name="detail" required maxLength={5000} rows={4} className={`${fieldControl} h-auto py-2`} />
          </Field>
          {error && (
            <div className="sm:col-span-2">
              <FormError>{error}</FormError>
            </div>
          )}
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Log activity" pending={log.isPending} />
      </form>
    </Dialog>
  );
}
