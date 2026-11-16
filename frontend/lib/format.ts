const shortDate = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" });

export const formatDate = (iso: string) => shortDate.format(new Date(iso));

const dayMonth = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", timeZone: "UTC" });
const longDay = new Intl.DateTimeFormat("en-US", { weekday: "long", month: "long", day: "numeric", timeZone: "UTC" });

/** "Jun 12". Accepts a date or a date-time string. */
export const formatDay = (iso: string) => dayMonth.format(new Date(iso.slice(0, 10)));
/** "Monday, June 16". */
export const formatLongDay = (iso: string) => longDay.format(new Date(iso.slice(0, 10)));
/** "09:30" from "2025-06-16T09:30". */
export const formatTime = (iso: string) => iso.slice(11, 16);

/** Whole days from one ISO date to another; negative when `to` is earlier. */
export const daysBetween = (from: string, to: string) =>
  Math.round((Date.parse(to.slice(0, 10)) - Date.parse(from.slice(0, 10))) / 86_400_000);

export function initials(name: string) {
  return name
    .split(" ")
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

export function cn(...parts: Array<string | false | null | undefined>) {
  return parts.filter(Boolean).join(" ");
}

/** The fields of a submitted form as trimmed strings, keyed by input name. */
export function readForm(form: HTMLFormElement): Record<string, string> {
  return Object.fromEntries([...new FormData(form)].map(([name, value]) => [name, String(value).trim()]));
}
