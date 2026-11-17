/** The date ranges you can report on. All in UTC, like the server. */
export const RANGES = {
  month: {
    label: "This month",
    get: (n: Date) => [
      new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth(), 1)),
      new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth() + 1, 0)),
    ],
  },
  lastMonth: {
    label: "Last month",
    get: (n: Date) => [
      new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth() - 1, 1)),
      new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth(), 0)),
    ],
  },
  quarter: { label: "Last 90 days", get: (n: Date) => [new Date(n.getTime() - 89 * 86_400_000), n] },
  year: { label: "This year", get: (n: Date) => [new Date(Date.UTC(n.getUTCFullYear(), 0, 1)), n] },
} as const;

export type RangeId = keyof typeof RANGES;

const iso = (d: Date) => d.toISOString().slice(0, 10);

/** The start and end day of a range, as "2026-11-01" strings. */
export const rangeDays = (id: RangeId, now = new Date()) => RANGES[id].get(now).map(iso) as [string, string];
