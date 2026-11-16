import type { Deal, PipelineStage } from "@/lib/deals";
import { moneyValue } from "@/lib/money";

/** Converts an amount to the reporting currency. Null when there is no exchange rate for it. */
export type Convert = (amount: number, currency: string) => number | null;

export type SortOrder = "value" | "recent";

export const isOpen = (deal: Deal) => !deal.closedAt;

/** The total of the deals in the reporting currency, or null if any of them cannot be converted. */
export function sumInReporting(deals: Deal[], convert: Convert) {
  let total = 0;
  for (const deal of deals) {
    const converted = convert(moneyValue(deal.amount), deal.amount.currency);
    if (converted == null) return null;
    total += converted;
  }
  return total;
}

/** Each open deal's value times its stage's chance to win. */
export function weightedForecast(open: Deal[], stages: Map<string, PipelineStage>, convert: Convert) {
  let total = 0;
  for (const deal of open) {
    const converted = convert(moneyValue(deal.amount), deal.amount.currency);
    if (converted == null) return null;
    total += (converted * (stages.get(deal.stageCode)?.winProbability ?? 0)) / 100;
  }
  return total;
}

/** Share of closed deals that were won, in percent. Null when nothing has closed. */
export function winRate(won: number, lost: number) {
  const closed = won + lost;
  return closed === 0 ? null : Math.round((won / closed) * 100);
}

/** Orders the cards of one column: highest value first, or newest first. Falls back to newest when a rate is missing. */
export function sortCards(cards: Deal[], order: SortOrder, convert: Convert) {
  return [...cards].sort((a, b) => {
    if (order === "recent") return b.updatedAt.localeCompare(a.updatedAt);
    const aValue = convert(moneyValue(a.amount), a.amount.currency);
    const bValue = convert(moneyValue(b.amount), b.amount.currency);
    return aValue == null || bValue == null ? b.updatedAt.localeCompare(a.updatedAt) : bValue - aValue;
  });
}
