import { EditDeleteActions } from "@/components/ui/icon-action";
import { Avatar, Card } from "@/components/ui/primitives";
import { FOLLOW_UP_TYPE_LABELS } from "@/lib/follow-ups";
import { cn, formatDay, formatLongDay, formatTime } from "@/lib/format";
import { CalendarCheck, Check } from "lucide-react";
import { TYPE_STYLES, type Item } from "./follow-up-item";

/** The follow-ups of the picked day, and below them everything that is overdue. */
export function FollowUpAgenda({ day, isToday, items, overdue, onToggle, onEdit, onDelete }: { day: string; isToday: boolean; items: Item[]; overdue: Item[]; onToggle: (item: Item) => void; onEdit: (item: Item) => void; onDelete: (item: Item) => void }) {
  return (
    <Card>
      <header className="border-b border-line px-5 py-4">
        <h2 className="text-[15px] font-semibold tracking-[-0.01em] text-ink">{isToday ? "Today's agenda" : "Agenda"}</h2>
        <p className="mt-0.5 text-[13px] text-ink-3">{formatLongDay(day)}</p>
      </header>
      {items.length === 0 ? (
        <div className="flex flex-col items-center px-6 py-10 text-center">
          <CalendarCheck className="size-6 text-ink-3" aria-hidden />
          <p className="mt-2 text-[13px] text-ink-3">Nothing scheduled for this day.</p>
        </div>
      ) : (
        <ul className="divide-y divide-line">
          {items.map((f) => (
            <AgendaItem key={f.id} item={f} onToggle={onToggle} onEdit={onEdit} onDelete={onDelete} when={formatTime(f.local)} />
          ))}
        </ul>
      )}

      {overdue.length > 0 && (
        <>
          <h3 className="border-y border-line tone-red bg-[var(--tone-bg)] px-5 py-2 text-[12px] font-semibold tracking-wide text-[var(--tone-ink)] uppercase">Overdue · {overdue.length}</h3>
          <ul className="divide-y divide-line">
            {overdue.map((f) => (
              <AgendaItem key={f.id} item={f} onToggle={onToggle} onEdit={onEdit} onDelete={onDelete} when={formatDay(f.local)} late />
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}

function AgendaItem({
  item,
  onToggle,
  onEdit,
  onDelete,
  when,
  late,
}: {
  item: Item;
  onToggle: (item: Item) => void;
  onEdit: (item: Item) => void;
  onDelete: (item: Item) => void;
  when: string;
  late?: boolean;
}) {
  const done = item.done;
  return (
    <li className="flex gap-3 px-5 py-3.5">
      <span className={cn("tnum w-11 shrink-0 pt-0.5 text-[12px]", late ? "font-medium text-down" : "text-ink-3")}>{when}</span>
      <span className={cn("w-0.5 shrink-0 rounded-full", done ? "bg-line-strong" : TYPE_STYLES[item.type].bar)} aria-hidden />
      <div className="min-w-0 flex-1">
        <p className={cn("truncate text-[13.5px] font-medium", done ? "text-ink-3 line-through" : "text-ink")}>{item.title}</p>
        <p className="truncate text-[12.5px] text-ink-3">
          {FOLLOW_UP_TYPE_LABELS[item.type]}
          {item.about ? ` · ${item.about.name}${item.about.company ? `, ${item.about.company}` : ""}` : ""}
        </p>
        <div className="mt-1.5 flex items-center justify-between gap-2">
          <p className="flex min-w-0 items-center gap-1.5 text-[12px] text-ink-2">
            <Avatar name={item.assigneeName ?? "?"} />
            <span className="truncate">{item.assigneeName}</span>
          </p>
          <EditDeleteActions name={item.title} onEdit={() => onEdit(item)} onDelete={() => onDelete(item)} />
        </div>
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
