import type { FollowUp, FollowUpType } from "@/lib/follow-ups";
import { localIso } from "@/lib/format";

/** A follow-up with its due time in the browser's time zone, ready to show. */
export type Item = FollowUp & { local: string; done: boolean };

export const withLocal = (f: FollowUp): Item => ({ ...f, local: localIso(f.dueAt), done: f.status === "DONE" });

export const dayOf = (item: Item) => item.local.slice(0, 10);

export const byTime = (a: Item, b: Item) => a.local.localeCompare(b.local);

/** The colour of each kind of follow-up: a bar for dots and lists, a chip for the calendar. */
export const TYPE_STYLES: Record<FollowUpType, { bar: string; chip: string }> = {
  CALL: { bar: "bg-sky-500", chip: "border-sky-500 bg-sky-50 text-sky-800" },
  MEETING: { bar: "bg-violet-500", chip: "border-violet-500 bg-violet-50 text-violet-800" },
  EMAIL: { bar: "bg-amber-500", chip: "border-amber-500 bg-amber-50 text-amber-900" },
  TASK: { bar: "bg-slate-400", chip: "border-slate-400 bg-slate-100 text-slate-700" },
};
