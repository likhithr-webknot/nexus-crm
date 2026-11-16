import type { ActivityType } from "@/lib/activities";
import { daysBetween, formatLongDay, todayLocal } from "@/lib/format";
import { CalendarDays, Mail, Phone, StickyNote, Zap, type LucideIcon } from "lucide-react";

/** How each kind of activity looks and what it is called. */
export const TYPES: Record<ActivityType, { icon: LucideIcon; tone: string; plural: string; label: string }> = {
  CALL: { icon: Phone, tone: "bg-sky-50 text-sky-700 ring-sky-200", plural: "Calls", label: "Call" },
  EMAIL_LOG: { icon: Mail, tone: "bg-amber-50 text-amber-800 ring-amber-200", plural: "Emails", label: "Email" },
  MEETING: { icon: CalendarDays, tone: "bg-violet-50 text-violet-700 ring-violet-200", plural: "Meetings", label: "Meeting" },
  NOTE: { icon: StickyNote, tone: "bg-emerald-50 text-emerald-700 ring-emerald-200", plural: "Notes", label: "Note" },
  SYSTEM: { icon: Zap, tone: "bg-slate-100 text-slate-600 ring-slate-200", plural: "System", label: "System" },
};

/** "Today", "Yesterday", or the full date. */
export function dayHeading(iso: string) {
  const ago = daysBetween(iso, todayLocal());
  return ago === 0 ? "Today" : ago === 1 ? "Yesterday" : formatLongDay(iso);
}
