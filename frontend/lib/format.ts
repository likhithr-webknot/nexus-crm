const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

const shortDate = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" });

// Hand-rolled because Intl "compact" output differs between Node's and the
// browser's ICU builds, which causes hydration mismatches.
export function formatCompactUsd(n: number) {
  const abs = Math.abs(n);
  const [div, suffix] = abs >= 1e6 ? [1e6, "M"] : abs >= 1e3 ? [1e3, "K"] : [1, ""];
  const v = Math.round((n / div) * 10) / 10;
  return `$${Number.isInteger(v) ? v : v.toFixed(1)}${suffix}`;
}
export const formatUsd = (n: number) => usd.format(n);
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
