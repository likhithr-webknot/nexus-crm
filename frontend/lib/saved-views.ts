/* Saved list views (backend: SavedViewController). */

import { api, qs } from "@/lib/api";

export type ViewKind = "lead" | "contact" | "deal";

/** The filters of a list page, as strings. Empty ones are left out. */
export type ViewFilters = Record<string, string>;

export type SavedView = { id: string; entityType: ViewKind; name: string; filters: ViewFilters; shared: boolean; mine: boolean };

export const listSavedViews = (entityType: ViewKind) => api<SavedView[]>(`/saved-views${qs({ entityType })}`);

export const createSavedView = (body: { entityType: ViewKind; name: string; filters: ViewFilters; shared: boolean }) =>
  api<SavedView>("/saved-views", { method: "POST", body: JSON.stringify(body) });

export const deleteSavedView = (id: string) => api(`/saved-views/${id}`, { method: "DELETE" });
