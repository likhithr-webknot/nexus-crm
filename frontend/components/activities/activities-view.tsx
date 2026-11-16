"use client";

import { useState } from "react";
import { CalendarDays, Inbox, Mail, Phone, Plus, StickyNote, Zap, type LucideIcon } from "lucide-react";
import { CONTACTS, TODAY, type Activity, type ActivityType } from "@/lib/mock-data";
import {
  Avatar,
  Card,
  EmptyState,
  Field,
  Page,
  PageHeader,
  SESSION_ONLY,
  buttonPrimary,
  buttonSecondary,
  control,
  fieldControl,
} from "@/components/ui/primitives";
import { useSession } from "@/components/auth/session";
import { Dialog, DialogActions, DialogBody } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { cn, daysBetween, formatLongDay, formatTime, readForm } from "@/lib/format";

const TYPES: Record<ActivityType, { icon: LucideIcon; tone: string; plural: string }> = {
  Call: { icon: Phone, tone: "bg-sky-50 text-sky-700 ring-sky-200", plural: "Calls" },
  Email: { icon: Mail, tone: "bg-amber-50 text-amber-800 ring-amber-200", plural: "Emails" },
  Meeting: { icon: CalendarDays, tone: "bg-violet-50 text-violet-700 ring-violet-200", plural: "Meetings" },
  Note: { icon: StickyNote, tone: "bg-emerald-50 text-emerald-700 ring-emerald-200", plural: "Notes" },
  System: { icon: Zap, tone: "bg-slate-100 text-slate-600 ring-slate-200", plural: "System" },
};

function dayHeading(iso: string) {
  const ago = daysBetween(iso, TODAY);
  return ago === 0 ? "Today" : ago === 1 ? "Yesterday" : formatLongDay(iso);
}

export function ActivitiesView({ activities: initial }: { activities: Activity[] }) {
  const [activities, setActivities] = useState(initial);
  const [adding, setAdding] = useState(false);
  const [type, setType] = useState<ActivityType | "">("");
  const [actor, setActor] = useState("");

  const actors = [...new Set(activities.flatMap((a) => (a.actor ? [a.actor] : [])))].sort();
  const byActor = activities.filter((a) => !actor || a.actor === actor);
  const shown = byActor.filter((a) => !type || a.type === type);
  // Activities arrive newest first, so each day's entries are already together.
  const days = [...new Set(shown.map((a) => a.at.slice(0, 10)))];
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
                <span className="tnum text-ink-3">{byActor.filter((a) => !t || a.type === t).length}</span>
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
            {actors.map((a) => (
              <option key={a}>{a}</option>
            ))}
          </select>
        </div>

        {shown.length === 0 ? (
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
                  .filter((a) => a.at.startsWith(d))
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
                            <span className="text-[13.5px] font-medium text-ink">{a.title}</span>
                            <span className="text-[12.5px] text-ink-3">
                              {a.contact} · {a.company}
                            </span>
                          </p>
                          <p className="mt-0.5 max-w-prose text-[13px] text-ink-2">{a.detail}</p>
                          <p className="mt-1.5 flex items-center gap-1.5 text-[12px] text-ink-3">
                            {a.actor ? (
                              <>
                                <Avatar name={a.actor} />
                                {a.actor}
                              </>
                            ) : (
                              "Automatic"
                            )}
                          </p>
                        </div>
                        <time dateTime={a.at} className="tnum shrink-0 pt-0.5 text-[12px] text-ink-3">
                          {formatTime(a.at)}
                        </time>
                      </li>
                    );
                  })}
              </ol>
            </section>
          ))
        )}
      </Card>

      <LogActivityDialog
        open={adding}
        onClose={() => setAdding(false)}
        onAdd={(activity) => {
          setActivities((all) => [activity, ...all]);
          clear();
          toast(`${activity.type} logged. ${SESSION_ONLY}`);
        }}
      />
    </Page>
  );
}

function LogActivityDialog({ open, onClose, onAdd }: { open: boolean; onClose: () => void; onAdd: (activity: Activity) => void }) {
  const user = useSession();

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    const contact = CONTACTS.find((c) => c.id === f.contact)!;
    // The fixtures live on one fixed day; the time is the real time it was logged.
    const now = new Date();
    const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
    onAdd({
      id: `a-${now.getTime()}`,
      type: f.type as ActivityType,
      title: f.title,
      detail: f.detail,
      contact: contact.name,
      company: contact.company,
      actor: user.displayName,
      at: `${TODAY}T${time}`,
    });
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} title="Log activity" description="Record something that happened with a contact.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Type">
            <select name="type" className={fieldControl}>
              {/* System entries are written by the app, never by hand. */}
              {(Object.keys(TYPES) as ActivityType[])
                .filter((t) => t !== "System")
                .map((t) => (
                  <option key={t}>{t}</option>
                ))}
            </select>
          </Field>
          <Field label="Contact">
            <select name="contact" required defaultValue="" className={fieldControl}>
              <option value="" disabled>
                Choose a contact
              </option>
              {CONTACTS.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}, {c.company}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Summary" className="sm:col-span-2">
            <input name="title" required maxLength={120} autoComplete="off" placeholder="Pricing call" className={fieldControl} />
          </Field>
          <Field label="Notes" className="sm:col-span-2">
            <textarea name="detail" required maxLength={2000} rows={4} className={`${fieldControl} h-auto py-2`} />
          </Field>
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Log activity" />
      </form>
    </Dialog>
  );
}
