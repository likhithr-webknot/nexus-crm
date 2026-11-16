/* The leads API (backend: LeadController). */

import { api, qs } from "@/lib/api";
import type { Money } from "@/lib/money";
import type { PageResponse, Ref } from "@/lib/page";
import type { LeadSource, LeadStatus } from "@/lib/stages";

export type Lead = {
  id: string;
  firstName: string;
  lastName: string;
  name: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  jobTitle: string | null;
  source: LeadSource;
  status: LeadStatus;
  owner: Ref | null;
  estimatedValue: Money | null;
  disqualifyReason: string | null;
  convertedDealId: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LeadView = "ALL" | "NEW_THIS_MONTH" | "QUALIFIED" | "UNASSIGNED";

export type LeadFilters = {
  q?: string;
  source?: LeadSource | "";
  status?: LeadStatus | "";
  /** A user ID, "me" or "unassigned". */
  ownerId?: string;
  view?: LeadView;
  page?: number;
  size?: number;
};

export type LeadSummary = {
  total: number;
  newThisMonth: number;
  qualified: number;
  unassigned: number;
  active: number;
  /** Percent change against last month. Null when there is nothing to compare with. */
  totalChange: number | null;
  newThisMonthChange: number | null;
};

export type NewLead = {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  companyName?: string;
  source: LeadSource;
  ownerId?: string;
  unassigned?: boolean;
  estimatedValue?: Money;
};

export type ImportRow = {
  name: string;
  email: string;
  company: string;
  phone: string;
  source: string;
  value: string;
  currency: string;
};

export type ImportResult = { total: number; imported: number; errors: { row: number; reason: string }[] };

export const listLeads = (filters: LeadFilters) =>
  api<PageResponse<Lead>>(`/leads${qs({ ...filters, view: filters.view === "ALL" ? undefined : filters.view })}`);

export const leadSummary = () => api<LeadSummary>("/leads/summary");

export const createLead = (body: NewLead) => api<Lead>("/leads", { method: "POST", body: JSON.stringify(body) });
export const getLead = (id: string) => api<Lead>(`/leads/${id}`);

export const assignLead = (id: string, ownerId: string | null) =>
  api<Lead>(`/leads/${id}/assign`, { method: "POST", body: JSON.stringify({ ownerId }) });

export const changeLeadStatus = (id: string, status: LeadStatus, reason?: string) =>
  api<Lead>(`/leads/${id}/status`, { method: "POST", body: JSON.stringify({ status, reason }) });

export const convertLead = (id: string, body: { dealName?: string; amount?: Money } = {}) =>
  api<{ leadId: string; companyId: string; contactId: string; dealId: string }>(`/leads/${id}/convert`, {
    method: "POST",
    body: JSON.stringify(body),
  });

export const deleteLeads = (ids: string[]) =>
  api("/leads/bulk-delete", { method: "POST", body: JSON.stringify({ ids }) });

export const importLeads = (rows: ImportRow[]) =>
  api<ImportResult>("/leads/imports", { method: "POST", body: JSON.stringify({ rows }) });

/** Every lead that matches the filters, for exporting. Reads page by page, up to a limit. */
export async function listAllLeads(filters: LeadFilters, limit = 5000) {
  const all: Lead[] = [];
  for (let page = 1; all.length < limit; page++) {
    const result = await listLeads({ ...filters, page, size: 100 });
    all.push(...result.items);
    if (all.length >= result.total || result.items.length === 0) break;
  }
  return all;
}

export type LeadChanges = Partial<Omit<NewLead, "ownerId" | "unassigned">> & { version?: number };

export const updateLead = (id: string, body: LeadChanges) =>
  api<Lead>(`/leads/${id}`, { method: "PATCH", body: JSON.stringify(body) });
