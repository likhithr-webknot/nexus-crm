"use client";

import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Download, Plus, Search, SearchX, SlidersHorizontal, Upload } from "lucide-react";
import { LEAD_VIEW_DELTAS, SOURCES, STAGES, THIS_MONTH, type Lead, type Source, type Stage } from "@/lib/mock-data";
import {
  Avatar,
  Card,
  EmptyState,
  NEEDS_API,
  Page,
  PageHeader,
  StageBadge,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { downloadCsv } from "@/lib/csv";
import { Popover } from "@/components/ui/popover";
import { cn, formatDate } from "@/lib/format";

const PAGE_SIZE = 8;
const UNASSIGNED = "unassigned";

type View = "all" | "new" | "qualified" | "unassigned";

// The tiles double as quick views. "invert" marks a count where lower is better.
const VIEWS: { id: View; label: string; match: (l: Lead) => boolean; invert?: boolean }[] = [
  { id: "all", label: "All leads", match: () => true },
  { id: "new", label: "New this month", match: (l) => l.createdAt.startsWith(THIS_MONTH) },
  { id: "qualified", label: "Qualified", match: (l) => l.stage === "Qualified" },
  { id: "unassigned", label: "Unassigned", match: (l) => l.owner === null, invert: true },
];

const EMPTY_FILTERS = { query: "", source: "" as Source | "", stage: "" as Stage | "", owner: "" };

export function LeadsView({ leads }: { leads: Lead[] }) {
  const [view, setView] = useState<View>("all");
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());

  // Any change to what is listed goes back to the first page.
  const updateFilters = (patch: Partial<typeof EMPTY_FILTERS>) => {
    setFilters((f) => ({ ...f, ...patch }));
    setPage(1);
  };
  const changeView = (next: View) => {
    setView(next);
    setPage(1);
  };
  const clearAll = () => {
    setFilters(EMPTY_FILTERS);
    changeView("all");
  };

  const owners = useMemo(() => [...new Set(leads.flatMap((l) => (l.owner ? [l.owner] : [])))].sort(), [leads]);

  const filtered = useMemo(() => {
    const q = filters.query.trim().toLowerCase();
    const inView = VIEWS.find((v) => v.id === view)!.match;
    return leads.filter(
      (l) =>
        inView(l) &&
        (!filters.source || l.source === filters.source) &&
        (!filters.stage || l.stage === filters.stage) &&
        (!filters.owner || (filters.owner === UNASSIGNED ? l.owner === null : l.owner === filters.owner)) &&
        (!q || [l.name, l.company, l.email].some((field) => field.toLowerCase().includes(q))),
    );
  }, [leads, view, filters]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const current = Math.min(page, pageCount);
  const rows = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE);

  const selectedOnPage = rows.filter((l) => selected.has(l.id)).length;
  const allOnPage = rows.length > 0 && selectedOnPage === rows.length;
  const toggle = (ids: string[], on: boolean) =>
    setSelected((prev) => {
      const next = new Set(prev);
      ids.forEach((id) => (on ? next.add(id) : next.delete(id)));
      return next;
    });

  const active = leads.filter((l) => l.stage !== "Won" && l.stage !== "Lost").length;
  const filtersActive = view !== "all" || Object.values(filters).some(Boolean);
  // Export what is ticked, or everything that matches when nothing is ticked.
  const toExport = selected.size ? leads.filter((l) => selected.has(l.id)) : filtered;

  const exportCsv = () =>
    downloadCsv(
      "leads.csv",
      ["Lead name", "Company", "Email", "Phone", "Source", "Assigned to", "Stage", "Created"],
      toExport.map((l) => [l.name, l.company, l.email, l.phone, l.source, l.owner ?? "", l.stage, l.createdAt]),
    );

  return (
    <Page>
      <PageHeader
        title="Leads"
        subtitle={
          <>
            Manage and nurture your <span className="tnum font-semibold text-ink">{active} active leads</span>.
          </>
        }
      >
          <button className={buttonSecondary} disabled title={NEEDS_API}>
            <Upload className="size-4" aria-hidden />
            Import
          </button>
          <button className={buttonSecondary} disabled={toExport.length === 0} onClick={exportCsv}>
            <Download className="size-4" aria-hidden />
            {selected.size ? `Export ${selected.size} selected` : "Export"}
          </button>
        <button className={buttonPrimary} disabled title={NEEDS_API}>
          <Plus className="size-4" aria-hidden />
          Add lead
        </button>
      </PageHeader>

      <div role="group" aria-label="Lead views" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {VIEWS.map((v) => {
          const delta = LEAD_VIEW_DELTAS[v.id];
          const good = v.invert ? delta <= 0 : delta >= 0;
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
                  {leads.filter(v.match).length}
                </span>
                <span className={cn("tnum text-[12px] font-semibold", good ? "text-up" : "text-down")}>
                  {delta > 0 ? "+" : ""}
                  {delta}%
                </span>
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
              onChange={(e) => updateFilters({ source: e.target.value as Source | "" })}
              className={cn(control, "px-2.5")}
            >
              <option value="">All sources</option>
              {SOURCES.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
            <select
              aria-label="Stage"
              value={filters.stage}
              onChange={(e) => updateFilters({ stage: e.target.value as Stage | "" })}
              className={cn(control, "px-2.5")}
            >
              <option value="">All stages</option>
              {STAGES.map((s) => (
                <option key={s}>{s}</option>
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
                  {owners.map((o) => (
                    <option key={o}>{o}</option>
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

        {rows.length === 0 ? (
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
            <table className="w-full min-w-[900px] text-left text-[13px]">
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
                      onChange={(e) =>
                        toggle(
                          rows.map((l) => l.id),
                          e.target.checked,
                        )
                      }
                    />
                  </th>
                  {["Lead", "Contact", "Source", "Assigned to", "Stage"].map((h) => (
                    <th key={h} className="px-3 py-2.5 font-medium">
                      {h}
                    </th>
                  ))}
                  <th className="py-2.5 pr-5 pl-3 text-right font-medium">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {rows.map((lead) => {
                  const isSelected = selected.has(lead.id);
                  return (
                    <tr key={lead.id} className={isSelected ? "bg-brand-soft/50" : "hover:bg-canvas/60"}>
                      <td className="py-3 pl-5">
                        <input
                          type="checkbox"
                          aria-label={`Select ${lead.name}`}
                          className="block size-4 accent-brand"
                          checked={isSelected}
                          onChange={(e) => toggle([lead.id], e.target.checked)}
                        />
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex items-center gap-3">
                          <Avatar name={lead.name} />
                          <div className="min-w-0">
                            <p className="font-medium whitespace-nowrap text-ink">{lead.name}</p>
                            <p className="text-[12.5px] whitespace-nowrap text-ink-3">{lead.company}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        <a href={`mailto:${lead.email}`} className="block text-ink-2 hover:text-brand hover:underline">
                          {lead.email}
                        </a>
                        <a
                          href={`tel:${lead.phone.replace(/[^+\d]/g, "")}`}
                          className="tnum block text-[12.5px] whitespace-nowrap text-ink-3 hover:text-brand hover:underline"
                        >
                          {lead.phone}
                        </a>
                      </td>
                      <td className="px-3 py-3 whitespace-nowrap text-ink-2">{lead.source}</td>
                      <td className="px-3 py-3 whitespace-nowrap">
                        {lead.owner ? (
                          <div className="flex items-center gap-2 text-ink-2">
                            <Avatar name={lead.owner} />
                            {lead.owner}
                          </div>
                        ) : (
                          <span className="text-ink-3">Unassigned</span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <StageBadge stage={lead.stage} />
                      </td>
                      <td className="tnum py-3 pr-5 pl-3 text-right whitespace-nowrap text-ink-2">
                        {formatDate(lead.createdAt)}
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
              Showing {(current - 1) * PAGE_SIZE + 1}–{(current - 1) * PAGE_SIZE + rows.length} of {filtered.length} leads
              {selected.size > 0 && (
                <>
                  {" · "}
                  {selected.size} selected{" "}
                  <button onClick={() => setSelected(new Set())} className="font-medium text-brand hover:underline">
                    Clear
                  </button>
                </>
              )}
            </p>
            <Pagination current={current} pageCount={pageCount} onChange={setPage} />
          </div>
        )}
      </Card>
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
