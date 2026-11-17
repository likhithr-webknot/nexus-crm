import { useDebounced } from "@/hooks/use-debounced";
import type { LeadFilters, LeadView } from "@/lib/leads";
import type { ViewFilters } from "@/lib/saved-views";
import type { LeadSource, LeadStatus } from "@/lib/stages";
import { useState } from "react";

export const PAGE_SIZE = 10;

export const EMPTY_FILTERS = { query: "", source: "" as LeadSource | "", status: "" as LeadStatus | "", owner: "", tag: "" };

export type LeadFilterState = typeof EMPTY_FILTERS;

/** What the leads list is filtered by, which page it shows, and the matching API filters. */
export function useLeadFilters() {
  const [view, setView] = useState<LeadView>("ALL");
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  const search = useDebounced(filters.query);

  const apiFilters: LeadFilters = { q: search.trim(), source: filters.source, status: filters.status, ownerId: filters.owner, tag: filters.tag, view };

  // Any change to what is listed goes back to the first page.
  const update = (patch: Partial<LeadFilterState>) => {
    setFilters((f) => ({ ...f, ...patch }));
    setPage(1);
  };
  const changeView = (next: LeadView) => {
    setView(next);
    setPage(1);
  };
  const clearAll = () => {
    setFilters(EMPTY_FILTERS);
    changeView("ALL");
  };

  /** Shows the filters of a saved view. Anything the view does not mention is cleared. */
  const apply = (saved: ViewFilters) => {
    setFilters({
      query: saved.q ?? "",
      source: (saved.source ?? "") as LeadFilterState["source"],
      status: (saved.status ?? "") as LeadFilterState["status"],
      owner: saved.ownerId ?? "",
      tag: saved.tag ?? "",
    });
    setView((saved.view as LeadView) ?? "ALL");
    setPage(1);
  };

  /** The filters on screen, in the shape a saved view stores them. */
  const saveable: ViewFilters = {
    q: filters.query.trim(),
    source: filters.source,
    status: filters.status,
    ownerId: filters.owner,
    tag: filters.tag,
    view: view === "ALL" ? "" : view,
  };

  return {
    apply,
    saveable,
    view,
    filters,
    page,
    setPage,
    apiFilters,
    update,
    changeView,
    clearAll,
    active: view !== "ALL" || Object.values(filters).some(Boolean),
  };
}
