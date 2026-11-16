"use client";

import { createContext, use, useCallback, useMemo, useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { latestRates, listCurrencies, type CurrencyInfo } from "@/lib/currencies";
import {
  DEFAULT_REPORTING_CURRENCY,
  convertCurrency,
  formatCompactCurrency,
  formatCurrency,
  isCurrencyCode,
  type CurrencyCode,
} from "@/lib/currency";

const PREFERENCE_KEY = "nexus.currency.reporting";
const preferenceListeners = new Set<() => void>();

const currencyPreferenceStore = {
  subscribe(listener: () => void) {
    preferenceListeners.add(listener);
    return () => preferenceListeners.delete(listener);
  },
  get() {
    try {
      const saved = localStorage.getItem(PREFERENCE_KEY);
      return saved && isCurrencyCode(saved) ? saved : DEFAULT_REPORTING_CURRENCY;
    } catch {
      return DEFAULT_REPORTING_CURRENCY;
    }
  },
  getServerSnapshot: () => DEFAULT_REPORTING_CURRENCY,
  set(currency: CurrencyCode) {
    try {
      localStorage.setItem(PREFERENCE_KEY, currency);
    } catch {
      // The preference still applies for this session when storage is unavailable.
    }
    preferenceListeners.forEach((listener) => listener());
  },
};

type RateState =
  | { status: "loading"; rates: null; updatedAt: null; stale: false; error: null }
  | { status: "ready"; rates: Record<string, number>; updatedAt: string; stale: boolean; error: null }
  | { status: "error"; rates: null; updatedAt: null; stale: false; error: string };

type CurrencyContextValue = {
  reportingCurrency: CurrencyCode;
  setReportingCurrency: (currency: CurrencyCode) => void;
  rateState: RateState;
  /** The currencies an admin has enabled, for pickers. */
  currencies: CurrencyInfo[];
  format: (amount: number, currency: CurrencyCode) => string;
  formatReporting: (amount: number, currency: CurrencyCode) => string | null;
  formatCompactReporting: (amount: number, currency: CurrencyCode) => string | null;
  convertToReporting: (amount: number, currency: CurrencyCode) => number | null;
  refreshRates: () => void;
};

const CurrencyContext = createContext<CurrencyContextValue | null>(null);

export function CurrencyProvider({ children }: { children: React.ReactNode }) {
  const reportingCurrency = useSyncExternalStore(
    currencyPreferenceStore.subscribe,
    currencyPreferenceStore.get,
    currencyPreferenceStore.getServerSnapshot,
  );
  const ratesQuery = useQuery({ queryKey: ["exchange-rates"], queryFn: latestRates, staleTime: 5 * 60_000 });
  const currenciesQuery = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies, staleTime: 5 * 60_000 });

  // The API gives each rate as "1 unit = N INR". The conversion works with "units per INR", so invert them.
  const rateState = useMemo<RateState>(() => {
    if (ratesQuery.isError) {
      return { status: "error", rates: null, updatedAt: null, stale: false, error: "The server could not provide exchange rates." };
    }
    if (!ratesQuery.data) return { status: "loading", rates: null, updatedAt: null, stale: false, error: null };
    const base = currenciesQuery.data?.find((c) => c.base)?.code ?? DEFAULT_REPORTING_CURRENCY;
    const rates: Record<string, number> = { [base]: 1 };
    for (const rate of ratesQuery.data) {
      const perBase = 1 / Number(rate.rateToBase);
      if (Number.isFinite(perBase) && perBase > 0) rates[rate.currency] = perBase;
    }
    const newest = ratesQuery.data.map((r) => r.rateDate).sort().at(-1) ?? "";
    return { status: "ready", rates, updatedAt: newest, stale: ratesQuery.data.some((r) => r.stale), error: null };
  }, [ratesQuery.data, ratesQuery.isError, currenciesQuery.data]);
  const currencies = useMemo(() => (currenciesQuery.data ?? []).filter((c) => c.enabled), [currenciesQuery.data]);

  const setReportingCurrency = useCallback((currency: CurrencyCode) => {
    if (!isCurrencyCode(currency)) throw new Error(`Unsupported currency: ${currency}`);
    currencyPreferenceStore.set(currency);
  }, []);
  const refreshRates = useCallback(() => void ratesQuery.refetch(), [ratesQuery]);

  const convertToReporting = useMemo(
    () => (amount: number, currency: CurrencyCode) =>
      convertCurrency(amount, currency, reportingCurrency, rateState.status === "ready" ? rateState.rates : {}),
    [rateState, reportingCurrency],
  );
  const value = useMemo<CurrencyContextValue>(
    () => ({
      reportingCurrency,
      setReportingCurrency,
      rateState,
      currencies,
      refreshRates,
      format: formatCurrency,
      formatReporting: (amount, currency) => {
        const converted = convertToReporting(amount, currency);
        return converted == null ? null : formatCurrency(converted, reportingCurrency);
      },
      formatCompactReporting: (amount, currency) => {
        const converted = convertToReporting(amount, currency);
        return converted == null ? null : formatCompactCurrency(converted, reportingCurrency);
      },
      convertToReporting,
    }),
    [reportingCurrency, rateState, currencies, convertToReporting, refreshRates, setReportingCurrency],
  );

  return <CurrencyContext value={value}>{children}</CurrencyContext>;
}

export function useCurrency() {
  const context = use(CurrencyContext);
  if (!context) throw new Error("useCurrency must be used inside CurrencyProvider");
  return context;
}
