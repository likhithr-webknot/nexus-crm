"use client";

import { createContext, use, useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import {
  DEFAULT_REPORTING_CURRENCY,
  convertCurrency,
  formatCompactCurrency,
  formatCurrency,
  isCurrencyCode,
  type CurrencyCode,
} from "@/lib/currency";

const PREFERENCE_KEY = "nexus.currency.reporting";
const RATES_URL = "https://open.er-api.com/v6/latest/USD";
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

type RateResponse = {
  result?: unknown;
  rates?: Record<string, unknown>;
  time_last_update_utc?: unknown;
};

type RateState =
  | { status: "loading"; rates: null; updatedAt: null; error: null }
  | { status: "ready"; rates: Record<string, number>; updatedAt: string; error: null }
  | { status: "error"; rates: null; updatedAt: null; error: string };

type CurrencyContextValue = {
  reportingCurrency: CurrencyCode;
  setReportingCurrency: (currency: CurrencyCode) => void;
  rateState: RateState;
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
  const [rateState, setRateState] = useState<RateState>({
    status: "loading",
    rates: null,
    updatedAt: null,
    error: null,
  });
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    const timeout = window.setTimeout(() => controller.abort(), 10_000);
    const loadRates = async () => {
      try {
        const response = await fetch(RATES_URL, { signal: controller.signal });
        if (!response.ok) throw new Error(`Exchange-rate service returned ${response.status}.`);
        const data = (await response.json()) as RateResponse;
        if (data.result !== "success" || !data.rates || typeof data.time_last_update_utc !== "string") {
          throw new Error("Exchange-rate service returned an invalid response.");
        }

        const rates = Object.fromEntries(
          Object.entries(data.rates)
            .filter((entry): entry is [string, number] => typeof entry[1] === "number" && Number.isFinite(entry[1]) && entry[1] > 0),
        );
        rates.USD = 1;
        setRateState({ status: "ready", rates, updatedAt: data.time_last_update_utc, error: null });
      } catch (error) {
        if (!active) return;
        setRateState({
          status: "error",
          rates: null,
          updatedAt: null,
          error: error instanceof Error ? error.message : "Could not load exchange rates.",
        });
      } finally {
        window.clearTimeout(timeout);
      }
    };

    void loadRates();
    return () => {
      active = false;
      controller.abort();
      window.clearTimeout(timeout);
    };
  }, [refreshKey]);

  const setReportingCurrency = useCallback((currency: CurrencyCode) => {
    if (!isCurrencyCode(currency)) throw new Error(`Unsupported currency: ${currency}`);
    currencyPreferenceStore.set(currency);
  }, []);
  const refreshRates = useCallback(() => {
    setRateState({ status: "loading", rates: null, updatedAt: null, error: null });
    setRefreshKey((key) => key + 1);
  }, []);

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
    [reportingCurrency, rateState, convertToReporting, refreshRates, setReportingCurrency],
  );

  return <CurrencyContext value={value}>{children}</CurrencyContext>;
}

export function useCurrency() {
  const context = use(CurrencyContext);
  if (!context) throw new Error("useCurrency must be used inside CurrencyProvider");
  return context;
}
