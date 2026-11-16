import type { ActivityType } from "@/lib/activities";
import { daysBetween, formatLongDay, todayLocal } from "@/lib/format";
import { CalendarDays, Mail, Phone, StickyNote, Zap, type LucideIcon } from "lucide-react";

/** How each kind of activity looks and what it is called. */
export const TYPES: Record<ActivityType, { icon: LucideIcon; tone: string; plural: string; label: string }> = {
  CALL: { icon: Phone, tone: "tone tone-sky", plural: "Calls", label: "Call" },
  EMAIL_LOG: { icon: Mail, tone: "tone tone-amber", plural: "Emails", label: "Email" },
  MEETING: { icon: CalendarDays, tone: "tone tone-violet", plural: "Meetings", label: "Meeting" },
  NOTE: { icon: StickyNote, tone: "tone tone-green", plural: "Notes", label: "Note" },
  SYSTEM: { icon: Zap, tone: "tone tone-neutral", plural: "System", label: "System" },
};

/** "Today", "Yesterday", or the full date. */
export function dayHeading(iso: string) {
  const ago = daysBetween(iso, todayLocal());
  return ago === 0 ? "Today" : ago === 1 ? "Yesterday" : formatLongDay(iso);
}
