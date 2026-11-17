import { api } from "@/lib/api";

export type ReportDataset = "revenue" | "team" | "funnel" | "sources";
export type ReportChart = "bar" | "line" | "pie";
export type SavedReport = { id: string; name: string; dataset: ReportDataset; chart: ReportChart; createdAt: string };

export const savedReports = {
  list: () => api<SavedReport[]>("/saved-reports"),
  create: (value: Omit<SavedReport, "id" | "createdAt">) =>
    api<SavedReport>("/saved-reports", { method: "POST", body: JSON.stringify(value) }),
  remove: (id: string) => api(`/saved-reports/${id}`, { method: "DELETE" }),
};
