/* Dashboard and reports (backend: ReportingController). All money is in INR, the base currency. */

import { api, qs } from "@/lib/api";

export type Kpi = {
  id: "total" | "new" | "qualified" | "won" | "lost" | "revenue" | "conversion" | "average";
  value: number;
  /** Percent change against the period before. Null when there is nothing to compare with. */
  change: number | null;
  trend: number[];
};

export type Dashboard = {
  from: string;
  to: string;
  data: {
    kpis: Kpi[];
    leadSources: { source: string; count: number; share: number }[];
    pipeline: { stageCode: string; name: string; count: number; amount: number; unconverted: number }[];
  };
};

export type MonthRevenue = { month: string; revenue: number; target: string | null };

export type Overview = {
  funnel: { label: string; count: number }[];
  revenueBySource: { source: string; revenue: number }[];
  team: { userId: string; name: string; leads: number; dealsWon: number; revenue: number; conversion: number }[];
  averageDealSize: number;
  kpis: Kpi[];
};

export const dashboard = (from?: string, to?: string) => api<Dashboard>(`/dashboard/summary${qs({ from, to })}`);

export const revenueByMonth = (months = 6) => api<MonthRevenue[]>(`/reports/revenue${qs({ months })}`);

export const overview = (from?: string, to?: string) => api<Overview>(`/reports/overview${qs({ from, to })}`);

export const listTargets = (year: number) =>
  api<{ month: string; amount: string }[]>(`/revenue-targets${qs({ year })}`);

export const setTarget = (month: string, amount: string) =>
  api<{ month: string; amount: string }>(`/revenue-targets/${month}`, {
    method: "PUT",
    body: JSON.stringify({ amount }),
  });

export const deleteTarget = (month: string) => api(`/revenue-targets/${month}`, { method: "DELETE" });
