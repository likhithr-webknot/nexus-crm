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
