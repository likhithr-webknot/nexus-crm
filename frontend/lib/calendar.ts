/* Calendar maths. All in UTC so the server and the browser agree on which day a date is. */

export const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const monthName = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" });

export type Month = { year: number; month: number };
export type Cell = { iso: string; inMonth: boolean; number: number };

export const monthOf = (iso: string): Month => ({ year: Number(iso.slice(0, 4)), month: Number(iso.slice(5, 7)) - 1 });

export const monthTitle = ({ year, month }: Month) => monthName.format(new Date(Date.UTC(year, month, 1)));

export function shiftMonth({ year, month }: Month, by: number): Month {
  const d = new Date(Date.UTC(year, month + by, 1));
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() };
}

/** Every date shown for a month, padded to whole weeks. */
export function monthGrid({ year, month }: Month): Cell[] {
  const lead = new Date(Date.UTC(year, month, 1)).getUTCDay();
  const days = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  const cells = Math.ceil((lead + days) / 7) * 7;
  return Array.from({ length: cells }, (_, i) => {
    const date = new Date(Date.UTC(year, month, i - lead + 1));
    return { iso: date.toISOString().slice(0, 10), inMonth: date.getUTCMonth() === month, number: date.getUTCDate() };
  });
}

export const addDays = (iso: string, by: number) => new Date(Date.parse(iso) + by * 86_400_000).toISOString().slice(0, 10);
