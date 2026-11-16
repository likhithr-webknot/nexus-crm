import { EditDeleteActions } from "@/components/ui/icon-action";
import { Avatar } from "@/components/ui/primitives";
import { cn, formatTime } from "@/lib/format";
import { TYPES, dayHeading } from "./activity-types";
import type { FeedItem } from "./use-activities";

/** The timeline grouped by day. Each entry has an icon, what happened, who did it, and when. */
export function ActivityFeed({ days, items, canChange, onEdit, onDelete }: { days: string[]; items: FeedItem[]; canChange: (activity: FeedItem) => boolean; onEdit: (activity: FeedItem) => void; onDelete: (activity: FeedItem) => void }) {
  return (
    <>
      {days.map((day) => (
        <section key={day} aria-label={dayHeading(day)}>
          <h2 className="border-y border-line bg-canvas/60 px-5 py-2 text-[12px] font-semibold tracking-wide text-ink-3 uppercase">{dayHeading(day)}</h2>
          <ol className="px-5 py-2">
            {items
              .filter((a) => a.local.startsWith(day))
              .map((a, i, list) => (
                <ActivityEntry key={a.id} activity={a} joinsNext={i < list.length - 1} canChange={canChange(a)} onEdit={onEdit} onDelete={onDelete} />
              ))}
          </ol>
        </section>
      ))}
    </>
  );
}

function ActivityEntry({ activity: a, joinsNext, canChange, onEdit, onDelete }: { activity: FeedItem; joinsNext: boolean; canChange: boolean; onEdit: (activity: FeedItem) => void; onDelete: (activity: FeedItem) => void }) {
  const { icon: Icon, tone } = TYPES[a.type];
  return (
    <li className="relative flex gap-3.5 py-3">
      {/* Line joining this entry to the next one in the same day. */}
      {joinsNext && <span className="absolute top-12 bottom-0 left-[17px] w-px bg-line" aria-hidden />}
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
      <div className="flex shrink-0 flex-col items-end gap-1">
        <time dateTime={a.occurredAt} className="tnum pt-0.5 text-[12px] text-ink-3">
          {formatTime(a.local)}
        </time>
        {canChange && <EditDeleteActions name={a.subject} onEdit={() => onEdit(a)} onDelete={() => onDelete(a)} />}
      </div>
    </li>
  );
}
