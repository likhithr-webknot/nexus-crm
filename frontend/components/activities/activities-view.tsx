"use client";

import { useState } from "react";
import { CalendarDays, Inbox, Mail, Phone, Plus, StickyNote, Zap, type LucideIcon } from "lucide-react";
import { TODAY, type Activity, type ActivityType } from "@/lib/mock-data";
import {
  Avatar,
  Card,
  EmptyState,
  NEEDS_API,
  Page,
  PageHeader,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { cn, daysBetween, formatLongDay, formatTime } from "@/lib/format";

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

export function ActivitiesView({ activities }: { activities: Activity[] }) {
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
        <button className={buttonPrimary} disabled title={NEEDS_API}>
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
    </Page>
  );
}
