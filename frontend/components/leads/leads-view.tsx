"use client";

import { useEffect, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Download, MoreHorizontal, Plus, Search, SearchX, SlidersHorizontal, Upload, WifiOff } from "lucide-react";
import { AddLeadDialog, ImportLeadsDialog, LeadActionsDialog } from "./lead-dialogs";
import {
  Avatar,
  Card,
  EmptyState,
  Page,
  PageHeader,
  StageBadge,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { downloadCsv } from "@/lib/csv";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Popover } from "@/components/ui/popover";
import { toast } from "@/components/ui/toast";
import { cn, formatDate } from "@/lib/format";
import { useCurrency } from "@/components/currency/currency-provider";
import { errorMessage } from "@/lib/api";
import { deleteLeads, leadSummary, listAllLeads, listLeads, type Lead, type LeadFilters, type LeadView } from "@/lib/leads";
import { SOURCES, SOURCE_LABELS, LEAD_STATUSES, sourceLabel, stageLabel, type LeadSource, type LeadStatus } from "@/lib/stages";
import { listAssignable } from "@/lib/users";
import { moneyValue } from "@/lib/money";

const PAGE_SIZE = 10;
const UNASSIGNED = "unassigned";

// The tiles double as quick views. "invert" marks a count where lower is better.
const VIEWS: { id: LeadView; label: string; count: "total" | "newThisMonth" | "qualified" | "unassigned"; change?: "totalChange" | "newThisMonthChange"; invert?: boolean }[] = [
  { id: "ALL", label: "All leads", count: "total", change: "totalChange" },
  { id: "NEW_THIS_MONTH", label: "New this month", count: "newThisMonth", change: "newThisMonthChange" },
  { id: "QUALIFIED", label: "Qualified", count: "qualified" },
  { id: "UNASSIGNED", label: "Unassigned", count: "unassigned", invert: true },
];

const EMPTY_FILTERS = { query: "", source: "" as LeadSource | "", status: "" as LeadStatus | "", owner: "" };

/** The value after it has stopped changing for a moment, so typing does not send a request per key. */
function useDebounced<T>(value: T, ms = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(timer);
  }, [value, ms]);
  return debounced;
}

export function LeadsView() {
  const { format } = useCurrency();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<"add" | "import" | "delete" | null>(null);
  const [acting, setActing] = useState<Lead | null>(null);
  const [view, setView] = useState<LeadView>("ALL");
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  // Ticked leads are kept whole, so they can be exported even after the list moves to another page.
  const [selected, setSelected] = useState<ReadonlyMap<string, Lead>>(new Map());

  const search = useDebounced(filters.query);
  const apiFilters: LeadFilters = {
    q: search.trim(),
    source: filters.source,
    status: filters.status,
    ownerId: filters.owner,
    view,
  };
  const list = useQuery({
    queryKey: ["leads", apiFilters, page],
    queryFn: () => listLeads({ ...apiFilters, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });
  const summary = useQuery({ queryKey: ["lead-summary"], queryFn: leadSummary });
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable });

  const remove = useMutation({
    mutationFn: (ids: string[]) => deleteLeads(ids),
    onSuccess: (_, ids) => {
      toast(`${ids.length === 1 ? "1 lead" : `${ids.length} leads`} deleted.`);
      setSelected(new Map());
      setDialog(null);
      for (const key of ["leads", "lead-summary", "dashboard"]) void queryClient.invalidateQueries({ queryKey: [key] });
    },
  });

  // Any change to what is listed goes back to the first page.
  const updateFilters = (patch: Partial<typeof EMPTY_FILTERS>) => {
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

  const rows = list.data?.items ?? [];
  const total = list.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const current = Math.min(page, pageCount);

  const selectedOnPage = rows.filter((l) => selected.has(l.id)).length;
  const allOnPage = rows.length > 0 && selectedOnPage === rows.length;
  const toggle = (leads: Lead[], on: boolean) =>
    setSelected((prev) => {
      const next = new Map(prev);
      leads.forEach((l) => (on ? next.set(l.id, l) : next.delete(l.id)));
      return next;
    });

  const filtersActive = view !== "ALL" || Object.values(filters).some(Boolean);

  // Export what is ticked, or everything that matches when nothing is ticked.
  const exportCsv = async () => {
    try {
      const leads = selected.size ? [...selected.values()] : await listAllLeads(apiFilters);
      downloadCsv(
        "leads.csv",
        ["Lead name", "Company", "Email", "Phone", "Source", "Assigned to", "Stage", "Estimated value", "Currency", "Created"],
        leads.map((l) => [
          l.name,
          l.companyName ?? "",
          l.email ?? "",
          l.phone ?? "",
          sourceLabel(l.source),
          l.owner?.name ?? "",
          stageLabel(l.status),
          l.estimatedValue ? moneyValue(l.estimatedValue) : "",
          l.estimatedValue?.currency ?? "",
          l.createdAt.slice(0, 10),
        ]),
      );
    } catch (e) {
      toast(errorMessage(e, "The export failed. Try again."));
    }
  };

  return (
    <Page>
      <PageHeader
        title="Leads"
        subtitle={
          <>
            Manage and nurture your <span className="tnum font-semibold text-ink">{summary.data?.active ?? "…"} active leads</span>.
          </>
        }
      >
        <button className={buttonSecondary} onClick={() => setDialog("import")}>
          <Upload className="size-4" aria-hidden />
          Import
        </button>
        <button className={buttonSecondary} onClick={exportCsv}>
          <Download className="size-4" aria-hidden />
          {selected.size ? `Export ${selected.size} selected` : "Export"}
        </button>
        <button className={buttonPrimary} onClick={() => setDialog("add")}>
          <Plus className="size-4" aria-hidden />
          Add lead
        </button>
      </PageHeader>

      <div role="group" aria-label="Lead views" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {VIEWS.map((v) => {
          const delta = v.change ? summary.data?.[v.change] : null;
          const good = v.invert ? (delta ?? 0) <= 0 : (delta ?? 0) >= 0;
          return (
            <button
              key={v.id}
              type="button"
              aria-pressed={view === v.id}
              onClick={() => changeView(v.id)}
              className={cn(
                "rounded-xl border bg-surface px-4 py-3.5 text-left transition-colors",
                view === v.id ? "border-brand ring-1 ring-brand" : "border-line hover:border-line-strong",
              )}
            >
              <span className="block truncate text-[12.5px] font-medium text-ink-2">{v.label}</span>
              <span className="mt-2 flex items-baseline gap-2">
                <span className="tnum text-[24px] leading-none font-semibold tracking-[-0.02em] text-ink">
                  {summary.data ? summary.data[v.count] : "–"}
                </span>
                {delta != null && (
                  <span className={cn("tnum text-[12px] font-semibold", good ? "text-up" : "text-down")}>
                    {delta > 0 ? "+" : ""}
                    {delta}%
                  </span>
                )}
              </span>
            </button>
          );
        })}
      </div>

      <Card>
        <div className="flex flex-wrap items-center gap-2 p-4">
          <label className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <span className="sr-only">Search leads</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
            <input
              type="search"
              value={filters.query}
              onChange={(e) => updateFilters({ query: e.target.value })}
              placeholder="Search leads, company, email…"
              className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
            />
          </label>

          <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
            <select
              aria-label="Source"
              value={filters.source}
              onChange={(e) => updateFilters({ source: e.target.value as LeadSource | "" })}
              className={cn(control, "px-2.5")}
            >
              <option value="">All sources</option>
              {SOURCES.map((s) => (
                <option key={s} value={s}>
                  {SOURCE_LABELS[s]}
                </option>
              ))}
            </select>
            <select
              aria-label="Stage"
              value={filters.status}
              onChange={(e) => updateFilters({ status: e.target.value as LeadStatus | "" })}
              className={cn(control, "px-2.5")}
            >
              <option value="">All stages</option>
              {LEAD_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {stageLabel(s)}
                </option>
              ))}
            </select>
            <Popover
              label="More filters"
              className="w-64 p-4"
              trigger={
                <span className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-ink-2")}>
                  <SlidersHorizontal className="size-4" aria-hidden />
                  More filters
                  {filters.owner && <span className="size-1.5 rounded-full bg-brand" aria-hidden />}
                </span>
              }
            >
              <label className="block text-[12px] font-medium text-ink-2">
                Assigned to
                <select
                  value={filters.owner}
                  onChange={(e) => updateFilters({ owner: e.target.value })}
                  className={cn(control, "mt-1.5 w-full px-2.5 font-normal")}
                >
                  <option value="">Anyone</option>
                  <option value={UNASSIGNED}>Unassigned</option>
                  {(people.data ?? []).map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.displayName}
                    </option>
                  ))}
                </select>
              </label>
              <button
                onClick={clearAll}
                disabled={!filtersActive}
                className="mt-3 text-[13px] font-medium text-brand hover:underline disabled:text-ink-3 disabled:no-underline"
              >
                Clear all filters
              </button>
            </Popover>
          </div>
        </div>

        {list.isError && !list.data ? (
          <div className="border-t border-line">
            <EmptyState icon={WifiOff} title="Couldn't load leads" body="Check your connection, then try again.">
              <button onClick={() => list.refetch()} className={buttonSecondary}>
                Try again
              </button>
            </EmptyState>
          </div>
        ) : list.isPending ? (
          <p className="border-t border-line px-5 py-10 text-center text-[13px] text-ink-3" role="status">
            Loading leads…
          </p>
        ) : rows.length === 0 ? (
          <div className="border-t border-line">
            <EmptyState
              icon={SearchX}
              title="No leads match these filters"
              body="Try a different search, or clear the filters to see every lead."
            >
              <button onClick={clearAll} className={buttonSecondary}>
                Clear all filters
              </button>
            </EmptyState>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1000px] text-left text-[13px]">
              <thead>
                <tr className="border-y border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
                  <th className="w-11 py-2.5 pl-5">
                    <input
                      type="checkbox"
                      aria-label="Select all leads on this page"
                      className="block size-4 accent-brand"
                      checked={allOnPage}
                      ref={(el) => {
                        if (el) el.indeterminate = selectedOnPage > 0 && !allOnPage;
                      }}
                      onChange={(e) => toggle(rows, e.target.checked)}
                    />
                  </th>
                  {["Lead", "Contact", "Source", "Assigned to", "Stage"].map((h) => (
                    <th key={h} className="px-3 py-2.5 font-medium">
                      {h}
                    </th>
                  ))}
                  <th className="px-3 py-2.5 text-right font-medium">Estimated value</th>
                  <th className="py-2.5 pr-3 pl-3 text-right font-medium">Created</th>
                  <th className="w-12 py-2.5 pr-4"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {rows.map((lead) => {
                  const isSelected = selected.has(lead.id);
                  const email = lead.email ?? "";
                  const phone = lead.phone ?? "";
                  return (
                    <tr key={lead.id} className={isSelected ? "bg-brand-soft/50" : "hover:bg-canvas/60"}>
                      <td className="py-3 pl-5">
                        <input
                          type="checkbox"
                          aria-label={`Select ${lead.name}`}
                          className="block size-4 accent-brand"
                          checked={isSelected}
                          onChange={(e) => toggle([lead], e.target.checked)}
                        />
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex items-center gap-3">
                          <Avatar name={lead.name} />
                          <div className="min-w-0">
                            <p className="font-medium whitespace-nowrap text-ink">{lead.name}</p>
                            <p className="text-[12.5px] whitespace-nowrap text-ink-3">{lead.companyName}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        {email && (
                          <a href={`mailto:${email}`} className="block text-ink-2 hover:text-brand hover:underline">
                            {email}
                          </a>
                        )}
                        {phone && (
                          <a
                            href={`tel:${phone.replace(/[^+\d]/g, "")}`}
                            className="tnum block text-[12.5px] whitespace-nowrap text-ink-3 hover:text-brand hover:underline"
                          >
                            {phone}
                          </a>
                        )}
                      </td>
                      <td className="px-3 py-3 whitespace-nowrap text-ink-2">{sourceLabel(lead.source)}</td>
                      <td className="px-3 py-3 whitespace-nowrap">
                        {lead.owner ? (
                          <div className="flex items-center gap-2 text-ink-2">
                            <Avatar name={lead.owner.name} />
                            {lead.owner.name}
                          </div>
                        ) : (
                          <span className="text-ink-3">Unassigned</span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <StageBadge stage={lead.status} />
                      </td>
                      <td className="tnum px-3 py-3 text-right whitespace-nowrap font-medium text-ink-2">
                        {lead.estimatedValue ? format(moneyValue(lead.estimatedValue), lead.estimatedValue.currency) : "—"}
                      </td>
                      <td className="tnum py-3 pr-3 pl-3 text-right whitespace-nowrap text-ink-2">
                        {formatDate(lead.createdAt)}
                      </td>
                      <td className="py-3 pr-4 text-right">
                        <button
                          aria-label={`Actions for ${lead.name}`}
                          onClick={() => setActing(lead)}
                          className="grid size-8 place-items-center rounded-md text-ink-3 hover:bg-canvas hover:text-ink"
                        >
                          <MoreHorizontal className="size-4" aria-hidden />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {rows.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3">
            <p className="tnum text-[12.5px] text-ink-3" aria-live="polite">
              Showing {(current - 1) * PAGE_SIZE + 1}–{(current - 1) * PAGE_SIZE + rows.length} of {total} leads
              {selected.size > 0 && (
                <>
                  {" · "}
                  {selected.size} selected{" "}
                  <button onClick={() => setSelected(new Map())} className="font-medium text-brand hover:underline">
                    Clear
                  </button>
                  {" · "}
                  <button onClick={() => setDialog("delete")} className="font-medium text-down hover:underline">
                    Delete
                  </button>
                </>
              )}
            </p>
            <Pagination current={current} pageCount={pageCount} onChange={setPage} />
          </div>
        )}
      </Card>

      <AddLeadDialog open={dialog === "add"} onClose={() => setDialog(null)} />
      <ImportLeadsDialog open={dialog === "import"} onClose={() => setDialog(null)} />
      <LeadActionsDialog lead={acting} onClose={() => setActing(null)} />
      <ConfirmDialog
        open={dialog === "delete"}
        onClose={() => setDialog(null)}
        onConfirm={() => remove.mutate([...selected.keys()])}
        pending={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        title={selected.size === 1 ? "Delete this lead?" : `Delete ${selected.size} leads?`}
        body="They are removed from the list. An admin can recover them from the database if this was a mistake."
        confirmLabel="Delete"
      />
    </Page>
  );
}

function Pagination({ current, pageCount, onChange }: { current: number; pageCount: number; onChange: (p: number) => void }) {
  // First, last and the pages around the current one; a gap becomes "…".
  const pages = [...new Set([1, current - 1, current, current + 1, pageCount])]
    .filter((p) => p >= 1 && p <= pageCount)
    .sort((a, b) => a - b);
  const cell = "tnum grid h-8 min-w-8 place-items-center rounded-md border px-2 text-[12.5px] font-medium";
  const idle = "border-line text-ink-2 hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-40";

  return (
    <nav aria-label="Pagination" className="flex items-center gap-1">
      <button aria-label="Previous page" disabled={current === 1} onClick={() => onChange(current - 1)} className={cn(cell, idle)}>
        <ChevronLeft className="size-4" aria-hidden />
      </button>
      {pages.map((p, i) => (
        <span key={p} className="flex items-center gap-1">
          {i > 0 && p - pages[i - 1] > 1 && <span className="px-1 text-ink-3">…</span>}
          <button
            aria-label={`Page ${p}`}
            aria-current={p === current ? "page" : undefined}
            onClick={() => onChange(p)}
            className={cn(cell, p === current ? "border-brand bg-brand-soft text-brand-ink" : idle)}
          >
            {p}
          </button>
        </span>
      ))}
      <button aria-label="Next page" disabled={current === pageCount} onClick={() => onChange(current + 1)} className={cn(cell, idle)}>
        <ChevronRight className="size-4" aria-hidden />
      </button>
    </nav>
  );
}
