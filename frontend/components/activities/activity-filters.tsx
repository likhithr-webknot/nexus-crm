import { control } from "@/components/ui/primitives";
import type { ActivityType } from "@/lib/activities";
import { cn } from "@/lib/format";
import type { User } from "@/lib/users";
import { TYPES } from "./activity-types";

const OPTIONS = ["", ...Object.keys(TYPES)] as Array<ActivityType | "">;

/** The type chips (each with its count) and the team-member picker. */
export function ActivityFilters({
  type,
  actor,
  people,
  countFor,
  onType,
  onActor,
}: {
  type: ActivityType | "";
  actor: string;
  people: User[];
  countFor: (type: ActivityType | "") => number;
  onType: (type: ActivityType | "") => void;
  onActor: (actor: string) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 p-4">
      <div role="group" aria-label="Activity type" className="flex flex-wrap gap-1.5">
        {OPTIONS.map((t) => (
          <button
            key={t || "all"}
            aria-pressed={type === t}
            onClick={() => onType(t)}
            className={cn(
              "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12.5px] font-medium transition-colors",
              type === t ? "border-brand bg-brand-soft text-brand-ink" : "border-line text-ink-2 hover:border-line-strong hover:text-ink",
            )}
          >
            {t ? TYPES[t].plural : "All"}
            <span className="tnum text-ink-3">{countFor(t)}</span>
          </button>
        ))}
      </div>
      <select aria-label="Team member" value={actor} onChange={(e) => onActor(e.target.value)} className={cn(control, "px-2.5 sm:ml-auto")}>
        <option value="">Everyone</option>
        {people.map((u) => (
          <option key={u.id} value={u.id}>
            {u.displayName}
          </option>
        ))}
      </select>
    </div>
  );
}
