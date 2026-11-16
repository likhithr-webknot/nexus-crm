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

// ---- Dates and times ----------------------------------------------------------
// The API sends instants in UTC ("2026-11-12T09:30:00Z"). Screens show local time.

const pad = (n: number) => String(n).padStart(2, "0");

/** "2026-11-12T15:00" in the browser's time zone, from an instant or a Date. */
export function localIso(value: string | Date) {
  const d = typeof value === "string" ? new Date(value) : value;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Today in the browser's time zone, as "2026-11-12". */
export const todayLocal = () => localIso(new Date()).slice(0, 10);

/** The instant for a local date ("2026-11-12") and time ("09:30"), as the API expects it. */
export const toInstant = (date: string, time = "00:00") => new Date(`${date}T${time}`).toISOString();

/** Splits a full name at the last space: "Mary Jane Watson" is first name "Mary Jane", last name "Watson". */
export function splitName(fullName: string) {
  const name = fullName.trim();
  const space = name.lastIndexOf(" ");
  return space < 0 ? { firstName: name, lastName: "" } : { firstName: name.slice(0, space).trim(), lastName: name.slice(space + 1) };
}
