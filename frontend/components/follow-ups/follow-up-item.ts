import type { FollowUp, FollowUpType } from "@/lib/follow-ups";
import { localIso } from "@/lib/format";

/** A follow-up with its due time in the browser's time zone, ready to show. */
export type Item = FollowUp & { local: string; done: boolean };

export const withLocal = (f: FollowUp): Item => ({ ...f, local: localIso(f.dueAt), done: f.status === "DONE" });

export const dayOf = (item: Item) => item.local.slice(0, 10);

export const byTime = (a: Item, b: Item) => a.local.localeCompare(b.local);

/** The colour of each kind of follow-up: a bar for dots and lists, a chip for the calendar. */
export const TYPE_STYLES: Record<FollowUpType, { bar: string; chip: string }> = {
  CALL: { bar: "tone-solid tone-sky", chip: "tone-chip tone-sky" },
  MEETING: { bar: "tone-solid tone-violet", chip: "tone-chip tone-violet" },
  EMAIL: { bar: "tone-solid tone-amber", chip: "tone-chip tone-amber" },
  TASK: { bar: "tone-solid tone-neutral", chip: "tone-chip tone-neutral" },
};
