"use client";

import { DeleteDialog } from "@/components/ui/delete-dialog";
import { ConfirmDialog } from "@/components/ui/dialog";
import { LoadFailed, LoadingNote } from "@/components/ui/load-state";
import { Pagination } from "@/components/ui/pagination";
import { Card, EmptyState, Page, PageHeader, buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useAssignableUsers } from "@/hooks/use-people";
import { useTags } from "@/hooks/use-tags";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { deleteLeads, listAllLeads, type Lead } from "@/lib/leads";
import { AFTER } from "@/lib/query-keys";
import { Download, Plus, SearchX, Upload } from "lucide-react";
import { useState } from "react";
import { AddLeadDialog } from "./add-lead-dialog";
import { EditLeadDialog } from "./edit-lead-dialog";
import { ImportLeadsDialog } from "./import-leads-dialog";
import { LeadActionsDialog } from "./lead-actions-dialog";
import { exportLeads } from "./lead-export";
import { LeadBulkBar } from "./lead-bulk-bar";
import { LeadFilterBar } from "./lead-filter-bar";
import { LeadSummaryTiles } from "./lead-summary-tiles";
import { LeadTable } from "./lead-table";
import { PAGE_SIZE, useLeadFilters } from "./use-lead-filters";
import { useLeadSelection } from "./use-lead-selection";
import { useDeleteLeads, useLeadList, useLeadSummary } from "./use-leads";

/** The leads page. It holds no logic of its own: it wires the filters, the list and the dialogs together. */
export function LeadsView() {
  const [dialog, setDialog] = useState<"add" | "import" | "delete" | null>(null);
  const [acting, setActing] = useState<Lead | null>(null);
  const [editing, setEditing] = useState<Lead | null>(null);
  const [deleting, setDeleting] = useState<Lead | null>(null);
  const refresh = useRefresh();

  const { view, filters, page, setPage, apiFilters, update, changeView, clearAll, active, apply, saveable } = useLeadFilters();
  const list = useLeadList(apiFilters, page, PAGE_SIZE);
  const summary = useLeadSummary();
  const people = useAssignableUsers();
  const tags = useTags();

  const rows = list.data?.items ?? [];
  const total = list.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const current = Math.min(page, pageCount);

  const selection = useLeadSelection(rows);
  const remove = useDeleteLeads(() => {
    selection.clear();
    setDialog(null);
  });

  // Export what is ticked, or everything that matches when nothing is ticked.
  const exportCsv = async () => {
    try {
      exportLeads(selection.selected.size ? [...selection.selected.values()] : await listAllLeads(apiFilters));
    } catch (e) {
      toast(errorMessage(e, "The export failed. Try again."));
    }
  };

  const count = selection.selected.size;

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
          {count ? `Export ${count} selected` : "Export"}
        </button>
        <button className={buttonPrimary} onClick={() => setDialog("add")}>
          <Plus className="size-4" aria-hidden />
          Add lead
        </button>
      </PageHeader>

      <LeadSummaryTiles summary={summary.data} view={view} onChange={changeView} />

      <Card>
        <LeadFilterBar
          filters={filters}
          people={people.data ?? []}
          tags={tags.data ?? []}
          saveable={saveable}
          active={active}
          onChange={update}
          onClear={clearAll}
          onApplyView={apply}
        />

        {count > 0 && <LeadBulkBar ids={[...selection.selected.keys()]} onClear={selection.clear} onDelete={() => setDialog("delete")} />}

        {list.isError && !list.data ? (
          <div className="border-t border-line">
            <LoadFailed what="leads" onRetry={() => list.refetch()} />
          </div>
        ) : list.isPending ? (
          <LoadingNote what="leads" />
        ) : rows.length === 0 ? (
          <div className="border-t border-line">
            <EmptyState icon={SearchX} title="No leads match these filters" body="Try a different search, or clear the filters to see every lead.">
              <button onClick={clearAll} className={buttonSecondary}>
                Clear all filters
              </button>
            </EmptyState>
          </div>
        ) : (
          <LeadTable
            rows={rows}
            selected={selection.selected}
            allOnPage={selection.allOnPage}
            someOnPage={selection.someOnPage}
            onToggle={selection.toggle}
            onAct={setActing}
          />
        )}

        {rows.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3">
            <p className="tnum text-[12.5px] text-ink-3" aria-live="polite">
              Showing {(current - 1) * PAGE_SIZE + 1}–{(current - 1) * PAGE_SIZE + rows.length} of {total} leads
              {count > 0 && <> · {count} selected</>}
            </p>
            <Pagination current={current} pageCount={pageCount} onChange={setPage} />
          </div>
        )}
      </Card>

      <AddLeadDialog open={dialog === "add"} onClose={() => setDialog(null)} />
      <ImportLeadsDialog open={dialog === "import"} onClose={() => setDialog(null)} />
      <LeadActionsDialog lead={acting} onClose={() => setActing(null)} onEdit={setEditing} onDelete={setDeleting} />
      <EditLeadDialog lead={editing} onClose={() => setEditing(null)} />
      <DeleteDialog
        target={deleting}
        title={(l) => `Delete ${l.name}?`}
        body="The lead is moved to the recycle bin. A manager or admin can restore it from there for 30 days."
        successMessage={(l) => `${l.name} deleted.`}
        action={(l) => deleteLeads([l.id])}
        onClose={() => setDeleting(null)}
        onDeleted={() => void refresh(AFTER.lead)}
      />
      <ConfirmDialog
        open={dialog === "delete"}
        onClose={() => setDialog(null)}
        onConfirm={() => remove.mutate([...selection.selected.keys()])}
        pending={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        title={count === 1 ? "Delete this lead?" : `Delete ${count} leads?`}
        body="They are moved to the recycle bin. A manager or admin can restore them from there for 30 days."
        confirmLabel="Delete"
      />
    </Page>
  );
}
