/* Currencies and exchange rates (backend: CurrencyController). */

import { api } from "@/lib/api";

export type CurrencyInfo = { code: string; name: string; minorUnits: number; enabled: boolean; base: boolean };

/** One unit of `currency` is worth `rateToBase` units of the base currency (INR). */
export type ExchangeRate = { currency: string; rateToBase: string; rateDate: string; source: string; stale: boolean };

export const listCurrencies = () => api<CurrencyInfo[]>("/currencies");

export const latestRates = () => api<ExchangeRate[]>("/exchange-rates/latest");

export const setCurrencyEnabled = (code: string, enabled: boolean) =>
  api<CurrencyInfo>(`/currencies/${code}`, { method: "PATCH", body: JSON.stringify({ enabled }) });

export const overrideRate = (currency: string, date: string, rateToBase: string) =>
  api<ExchangeRate>(`/exchange-rates/${currency}/${date}`, { method: "PUT", body: JSON.stringify({ rateToBase }) });

/** Deletes a currency nobody uses. One that leads or deals still use has to be disabled instead. */
export const deleteCurrency = (code: string) => api(`/currencies/${code}`, { method: "DELETE" });

/** Takes back a rate that was typed in by hand. */
export const deleteRateOverride = (currency: string, date: string) =>
  api(`/exchange-rates/${currency}/${date}`, { method: "DELETE" });
