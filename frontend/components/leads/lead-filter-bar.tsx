import { SavedViewsMenu } from "@/components/saved-views/saved-views-menu";
import { Popover } from "@/components/ui/popover";
import { control } from "@/components/ui/primitives";
import { cn } from "@/lib/format";
import { LEAD_STATUSES, SOURCES, SOURCE_LABELS, stageLabel, type LeadSource, type LeadStatus } from "@/lib/stages";
import type { ViewFilters } from "@/lib/saved-views";
import type { Tag } from "@/lib/tags";
import type { User } from "@/lib/users";
import { Search, SlidersHorizontal } from "lucide-react";
import type { LeadFilterState } from "./use-lead-filters";

const UNASSIGNED = "unassigned";

export function LeadFilterBar({
  filters,
  people,
  tags,
  saveable,
  active,
  onChange,
  onClear,
  onApplyView,
}: {
  filters: LeadFilterState;
  people: User[];
  tags: Tag[];
  saveable: ViewFilters;
  onApplyView: (filters: ViewFilters) => void;
  active: boolean;
  onChange: (patch: Partial<LeadFilterState>) => void;
  onClear: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 p-4">
      <label className="relative min-w-[200px] flex-1 sm:max-w-xs">
        <span className="sr-only">Search leads</span>
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
        <input
          type="search"
          value={filters.query}
          onChange={(e) => onChange({ query: e.target.value })}
          placeholder="Search leads, company, email…"
          className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
        />
      </label>

      <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
        <select aria-label="Source" value={filters.source} onChange={(e) => onChange({ source: e.target.value as LeadSource | "" })} className={cn(control, "px-2.5")}>
          <option value="">All sources</option>
          {SOURCES.map((s) => (
            <option key={s} value={s}>
              {SOURCE_LABELS[s]}
            </option>
          ))}
        </select>
        <select aria-label="Stage" value={filters.status} onChange={(e) => onChange({ status: e.target.value as LeadStatus | "" })} className={cn(control, "px-2.5")}>
          <option value="">All stages</option>
          {LEAD_STATUSES.map((s) => (
            <option key={s} value={s}>
              {stageLabel(s)}
            </option>
          ))}
        </select>
        {tags.length > 0 && (
          <select aria-label="Tag" value={filters.tag} onChange={(e) => onChange({ tag: e.target.value })} className={cn(control, "px-2.5")}>
            <option value="">All tags</option>
            {tags.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        )}
        <SavedViewsMenu kind="lead" current={saveable} onApply={onApplyView} />
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
            <select value={filters.owner} onChange={(e) => onChange({ owner: e.target.value })} className={cn(control, "mt-1.5 w-full px-2.5 font-normal")}>
              <option value="">Anyone</option>
              <option value={UNASSIGNED}>Unassigned</option>
              {people.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </label>
          <button
            onClick={onClear}
            disabled={!active}
            className="mt-3 text-[13px] font-medium text-brand hover:underline disabled:text-ink-3 disabled:no-underline"
          >
            Clear all filters
          </button>
        </Popover>
      </div>
    </div>
  );
}
