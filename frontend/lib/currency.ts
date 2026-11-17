export type CurrencyCode = string;

const FALLBACK_CURRENCIES = ["AUD", "EUR", "GBP", "INR", "JPY", "SAR", "USD"];

const DISPLAY_NAMES = new Intl.DisplayNames(["en"], { type: "currency" });

const codes = new Set(
  typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("currency") : FALLBACK_CURRENCIES,
);
for (const code of FALLBACK_CURRENCIES) codes.add(code);

export const CURRENCIES = [...codes].sort().map((code) => ({ code, name: DISPLAY_NAMES.of(code) ?? code }));

export const DEFAULT_REPORTING_CURRENCY = "INR";

const LOCALE_BY_CURRENCY: Record<string, string> = {
  AUD: "en-AU",
  INR: "en-IN",
  SAR: "en-SA",
  USD: "en-US",
};

export function isCurrencyCode(value: string): value is CurrencyCode {
  return codes.has(value);
}

export function currencyMinorUnits(currency: CurrencyCode): number {
  return (
    new Intl.NumberFormat(LOCALE_BY_CURRENCY[currency] ?? "en", {
      style: "currency",
      currency,
    }).resolvedOptions().maximumFractionDigits ?? 2
  );
}

export function decimalPlaces(value: string) {
  const normalized = value.toLowerCase();
  const exponentIndex = normalized.indexOf("e");
  const mantissa = exponentIndex < 0 ? normalized : normalized.slice(0, exponentIndex);
  const exponentText = exponentIndex < 0 ? "0" : normalized.slice(exponentIndex + 1);
  const decimalIndex = mantissa.indexOf(".");
  const fractionalDigits = decimalIndex < 0 ? 0 : mantissa.length - decimalIndex - 1;
  const exponent = Number(exponentText);
  return Number.isFinite(exponent) ? Math.max(0, fractionalDigits - exponent) : Number.POSITIVE_INFINITY;
}

export function formatCurrency(amount: number, currency: CurrencyCode) {
  return new Intl.NumberFormat(LOCALE_BY_CURRENCY[currency] ?? "en", {
    style: "currency",
    currency,
  }).format(amount);
}

export function formatCompactCurrency(amount: number, currency: CurrencyCode) {
  const abs = Math.abs(amount);
  const [divisor, suffix] = abs >= 1e9 ? [1e9, "B"] : abs >= 1e6 ? [1e6, "M"] : abs >= 1e3 ? [1e3, "K"] : [1, ""];
  const value = Math.round((amount / divisor) * 10) / 10;
  const formatted = new Intl.NumberFormat(LOCALE_BY_CURRENCY[currency] ?? "en", {
    style: "currency",
    currency,
    maximumFractionDigits: Number.isInteger(value) ? 0 : 1,
  }).format(value);
  return `${formatted}${suffix}`;
}

export function convertCurrency(
  amount: number,
  from: CurrencyCode,
  to: CurrencyCode,
  rates: Readonly<Record<string, number>>,
) {
  if (from === to) return amount;
  const fromRate = rates[from];
  const toRate = rates[to];
  if (!Number.isFinite(fromRate) || fromRate <= 0 || !Number.isFinite(toRate) || toRate <= 0) return null;
  return (amount / fromRate) * toRate;
}
