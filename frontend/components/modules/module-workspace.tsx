"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ChevronLeft, ChevronRight, Download, Pencil, Plus, Search, SearchX } from "lucide-react";
import dynamic from "next/dynamic";
import { useState } from "react";
import { useSession } from "@/components/auth/session";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Drawer } from "@/components/ui/drawer";
import { LoadFailed } from "@/components/ui/load-state";
import { Badge, Card, EmptyState, Page, PageHeader, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useDebounced } from "@/hooks/use-debounced";
import { useRecordRoute } from "@/hooks/use-record-route";
import { api, errorMessage } from "@/lib/api";
import { downloadSheet } from "@/lib/export";
import { cn, formatDay } from "@/lib/format";
import { modulesApi, type ModuleKind, type ModuleRecord, type ModuleValues } from "@/lib/modules";
import { AutomationFlow } from "./automation-flow";
import { MODULES } from "./module-config";
import { QuoteBuilder } from "./quote-builder";
import { RecordForm } from "./record-form";

const CommunicationComposer = dynamic(() => import("./communication-composer").then((module) => module.CommunicationComposer), { ssr: false });

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const statusTone = (status: string) => (["Active", "Approved", "Published", "Sent", "Completed"].includes(status) ? "green" : ["Failed", "Declined"].includes(status) ? "red" : status === "Unknown" ? "amber" : status === "Draft" ? "neutral" : "brand");
const capital = (text: string) => text[0].toUpperCase() + text.slice(1);

/**
 * The list screen shared by campaigns, products, quotes, communications and automations.
 * The table takes the full width. Choosing a record opens it in a panel on the right, and the
 * address changes to /quotes/123 so the record can be linked to.
 */
export function ModuleWorkspace({ kind }: { kind: ModuleKind }) {
  const config = MODULES[kind];
  const role = useSession().role;
  const canWrite = kind === "products" || kind === "automations" ? role === "ADMIN" : kind === "campaigns" ? role !== "REP" : true;
  const isMail = kind === "communications";
  const client = useQueryClient();
  const route = useRecordRoute(`/${kind}`);

  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [sort, setSort] = useState<"name" | "updatedAt">("updatedAt");
  const [page, setPage] = useState(1);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [form, setForm] = useState<"create" | "edit" | null>(null);
  const [compose, setCompose] = useState<"create" | "edit" | null>(null);
  /** The record ids waiting for the user to confirm archiving. */
  const [archiving, setArchiving] = useState<string[] | null>(null);
  const [confirmUnsent, setConfirmUnsent] = useState(false);
  const [exporting, setExporting] = useState(false);

  // Search, status, sort and paging happen on the server.
  const term = useDebounced(query.trim());
  const params = { q: term, status, sort: sort === "name" ? ("name" as const) : ("updated" as const), page };
  const records = useQuery({ queryKey: ["workspace", kind, "list", params], queryFn: () => modulesApi.list(kind, params), staleTime: 30_000, placeholderData: keepPreviousData });
  const filtered = records.data?.items ?? [];
  const total = records.data?.total ?? 0;
  const pageSize = records.data?.size ?? 50;
  const inList = route.id ? filtered.find((record) => record.id === route.id) : undefined;
  // A linked record can be on another page, so fetch it by itself when the list does not hold it.
  const single = useQuery({ queryKey: ["workspace", kind, "record", route.id], queryFn: () => modulesApi.get(kind, route.id!), enabled: Boolean(route.id) && !inList, retry: false });
  const selected = inList ?? single.data;
  const filtering = Boolean(term || status);

  const refresh = () => client.invalidateQueries({ queryKey: ["workspace", kind] });
  const startNew = () => (isMail ? setCompose("create") : setForm("create"));
  const amount = (record: ModuleRecord) => (kind === "automations" ? String(record.amount) : money.format(record.amount));

  const save = async (values: ModuleValues) => {
    if (form === "edit" && selected) await modulesApi.update(kind, selected.id, values);
    else await modulesApi.create(kind, values);
    await refresh();
    toast(`${capital(config.singular)} saved.`);
  };
  const archive = async () => {
    const ids = archiving ?? [];
    try {
      await modulesApi.archive(kind, ids);
      setSelectedIds((current) => current.filter((id) => !ids.includes(id)));
      if (route.id && ids.includes(route.id)) route.close();
      await refresh();
      toast(`${ids.length} ${ids.length === 1 ? config.singular : `${config.singular}s`} archived. Restore from the recycle bin.`);
    } catch (cause) {
      toast(errorMessage(cause, "The records could not be archived."));
    } finally {
      setArchiving(null);
    }
  };
  const markUnsent = async () => {
    if (!selected) return;
    try {
      await api(`/mail/mark-unsent/${selected.id}`, { method: "POST" });
      await refresh();
      toast("Marked as not sent. You can reopen the composer and retry.");
    } catch (cause) {
      toast(errorMessage(cause, "The email status could not be updated."));
    } finally {
      setConfirmUnsent(false);
    }
  };
  const exportRows = async () => {
    setExporting(true);
    try {
      const header = ["Name", "Status", "Owner", "Account", ...(config.metric ? [config.metric] : []), "Updated"];
      // Every match, not only the page on screen.
      const rows = await modulesApi.listAll(kind, { q: term, status, sort: params.sort });
      await downloadSheet(kind, config.title, header, rows.map((record) => [record.name, record.status, record.owner, record.account, ...(config.metric ? [record.amount] : []), record.updatedAt.slice(0, 10)]));
      toast(`Exported ${rows.length} ${rows.length === 1 ? "record" : "records"}.`);
    } catch (cause) {
      toast(errorMessage(cause, "The export could not be created."));
    } finally {
      setExporting(false);
    }
  };

  const allVisibleSelected = filtered.length > 0 && filtered.every((record) => selectedIds.includes(record.id));

  return (
    <Page>
      <PageHeader title={config.title} subtitle={config.subtitle}>
        <button onClick={() => void exportRows()} disabled={!filtered.length || exporting} className={buttonSecondary}>
          <Download className="size-4" aria-hidden />
          {exporting ? "Exporting…" : "Export"}
        </button>
        {canWrite && (
          <button onClick={startNew} className={buttonPrimary}>
            <Plus className="size-4" aria-hidden />
            {isMail ? "Compose" : `New ${config.singular}`}
          </button>
        )}
      </PageHeader>

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 border-b border-line p-3">
          <label className="relative min-w-44 flex-1 sm:max-w-sm">
            <span className="sr-only">Search {config.title.toLowerCase()}</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
            <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder={`Search ${config.title.toLowerCase()}`} className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")} />
          </label>
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); }} className={cn(control, "px-2.5")} aria-label="Filter by status">
            <option value="">All statuses</option>
            {config.statuses.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
          <select value={sort} onChange={(event) => { setSort(event.target.value as "name" | "updatedAt"); setPage(1); }} className={cn(control, "px-2.5")} aria-label="Sort records">
            <option value="updatedAt">Recently updated</option>
            <option value="name">Name</option>
          </select>
          {records.data && (
            <p className="tnum ml-auto text-[12.5px] text-ink-3" aria-live="polite">
              {filtering ? `${total} matching` : `${total} ${total === 1 ? config.singular : `${config.singular}s`}`}
            </p>
          )}
        </div>

        {canWrite && selectedIds.length > 0 && (
          <div className="flex animate-[fade-in_var(--duration-menu)_ease-out] items-center gap-3 border-b border-line bg-brand-soft/60 px-4 py-2 text-[12.5px]">
            <span className="tnum font-medium">{selectedIds.length} selected</span>
            <button onClick={() => setSelectedIds([])} className="text-ink-2 hover:text-ink">
              Clear
            </button>
            <button onClick={() => setArchiving(selectedIds)} className="ml-auto inline-flex items-center gap-1.5 font-medium text-down hover:underline">
              <Archive className="size-3.5" aria-hidden />
              Archive
            </button>
          </div>
        )}

        {records.isError && !records.data ? (
          <LoadFailed what={config.title.toLowerCase()} onRetry={() => records.refetch()} />
        ) : records.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading records">
            {Array.from({ length: 6 }, (_, index) => (
              <div key={index} className="skeleton h-11" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title={filtering ? "No matching records" : `No ${config.title.toLowerCase()} yet`}
            body={filtering ? "Nothing matches the search and status you chose." : canWrite ? `Create your first ${config.singular} to get started.` : `Nothing has been added here yet.`}
          >
            {filtering ? (
              <button
                className={buttonSecondary}
                onClick={() => {
                  setQuery("");
                  setStatus("");
                  setPage(1);
                }}
              >
                Clear filters
              </button>
            ) : (
              canWrite && (
                <button className={buttonPrimary} onClick={startNew}>
                  {isMail ? "Compose email" : `Create ${config.singular}`}
                </button>
              )
            )}
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-[13px]">
              <thead className="text-[11.5px] text-ink-3">
                <tr>
                  {canWrite && (
                    <th className="w-10 border-b border-line py-2.5 pr-0 pl-4">
                      <input type="checkbox" aria-label="Select all shown" checked={allVisibleSelected} onChange={(event) => setSelectedIds(event.target.checked ? filtered.map((record) => record.id) : [])} />
                    </th>
                  )}
                  <th className="border-b border-line px-4 py-2.5 font-medium">Name</th>
                  <th className="border-b border-line px-4 py-2.5 font-medium">Status</th>
                  <th className="border-b border-line px-4 py-2.5 font-medium">Owner</th>
                  {config.metric && <th className="border-b border-line px-4 py-2.5 text-right font-medium">{config.metric}</th>}
                  <th className="border-b border-line px-4 py-2.5 text-right font-medium">Updated</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((record) => (
                  <tr
                    key={record.id}
                    onClick={() => route.open(record.id)}
                    className={cn("cursor-pointer transition-colors duration-[var(--duration-hover)]", route.id === record.id ? "bg-brand-soft/60" : "hover:bg-surface-muted")}
                  >
                    {canWrite && (
                      <td className="border-b border-line py-2.5 pr-0 pl-4" onClick={(event) => event.stopPropagation()}>
                        <input
                          type="checkbox"
                          aria-label={`Select ${record.name}`}
                          checked={selectedIds.includes(record.id)}
                          onChange={(event) => setSelectedIds((ids) => (event.target.checked ? [...ids, record.id] : ids.filter((id) => id !== record.id)))}
                        />
                      </td>
                    )}
                    <td className="max-w-96 border-b border-line px-4 py-2.5">
                      {/* The keyboard and screen-reader way in; the whole row is the mouse target. */}
                      <button onClick={() => route.open(record.id)} className="block w-full rounded text-left">
                        <span className="block truncate font-medium text-ink">{record.name}</span>
                        {(record.account || record.description) && <span className="mt-0.5 block truncate text-[12px] text-ink-3">{record.account || record.description}</span>}
                      </button>
                    </td>
                    <td className="border-b border-line px-4 py-2.5">
                      <Badge tone={statusTone(record.status)}>{record.status}</Badge>
                    </td>
                    <td className="border-b border-line px-4 py-2.5 text-ink-2">{record.owner}</td>
                    {config.metric && <td className="tnum border-b border-line px-4 py-2.5 text-right text-ink">{amount(record)}</td>}
                    <td className="tnum border-b border-line px-4 py-2.5 text-right text-ink-3">{formatDay(record.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {total > pageSize && (
          <div className="flex items-center justify-between border-t border-line px-4 py-2.5 text-[12.5px] text-ink-3">
            <span className="tnum" aria-live="polite">
              {(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)} of {total}
            </span>
            <div className="flex gap-2">
              <button className={buttonSecondary} disabled={page <= 1} onClick={() => setPage((current) => current - 1)} aria-label="Previous page">
                <ChevronLeft className="size-4" aria-hidden />
              </button>
              <button className={buttonSecondary} disabled={page * pageSize >= total} onClick={() => setPage((current) => current + 1)} aria-label="Next page">
                <ChevronRight className="size-4" aria-hidden />
              </button>
            </div>
          </div>
        )}
      </Card>
      <p className="text-[12.5px] text-ink-3">{config.note}</p>

      <Drawer
        open={Boolean(route.id)}
        onClose={route.close}
        size={kind === "quotes" || kind === "automations" ? "xl" : "md"}
        title={selected?.name ?? capital(config.singular)}
        subtitle={selected && <Badge tone={statusTone(selected.status)}>{selected.status}</Badge>}
        actions={
          selected &&
          canWrite && (
            <>
              {(!isMail || selected.status === "Draft" || selected.status === "Failed") && (
                <button onClick={() => (isMail ? setCompose("edit") : setForm("edit"))} className={buttonSecondary}>
                  <Pencil className="size-3.5" aria-hidden />
                  {isMail ? "Open composer" : "Edit"}
                </button>
              )}
              <button onClick={() => setArchiving([selected.id])} className={buttonSecondary} aria-label={`Archive ${selected.name}`}>
                <Archive className="size-3.5" aria-hidden />
              </button>
            </>
          )
        }
      >
        {!selected ? (
          records.isPending || single.isPending ? (
            <div className="skeleton h-40" role="status" aria-label="Loading record" />
          ) : (
            <Card>
              <EmptyState icon={SearchX} title={`This ${config.singular} is not available`} body="It may have been archived, or you may not have access to it." />
            </Card>
          )
        ) : (
          <>
            <Card>
              <dl className="divide-y divide-line px-5 text-[13px]">
                <Fact label="Owner">{selected.owner}</Fact>
                {selected.account && <Fact label="Account">{selected.account}</Fact>}
                {selected.recipient && <Fact label="To">{selected.recipient}</Fact>}
                {!!selected.cc?.length && <Addresses label="Cc" list={selected.cc} />}
                {!!selected.bcc?.length && <Addresses label="Bcc" list={selected.bcc} />}
                {config.metric && <Fact label={config.metric}>{amount(selected)}</Fact>}
                <Fact label="Updated">{formatDay(selected.updatedAt)}</Fact>
                <div className="py-3">
                  <dt className="text-ink-3">{isMail ? "Message" : "Details"}</dt>
                  <dd className="mt-1 leading-6 whitespace-pre-wrap">{selected.description || <span className="text-ink-3">Nothing added.</span>}</dd>
                </div>
              </dl>
              {isMail && selected.status === "Unknown" && (
                <div className="tone-amber border-t border-[var(--tone-line)] bg-[var(--tone-bg)] p-4 text-[12.5px] text-[var(--tone-ink)]">
                  <p>Gmail did not confirm this send. Check the connected account&apos;s Sent folder before retrying.</p>
                  <button onClick={() => setConfirmUnsent(true)} className="mt-2 font-semibold underline">
                    Not in Sent? Mark as unsent
                  </button>
                </div>
              )}
            </Card>
            {kind === "quotes" && <QuoteBuilder key={selected.id} quote={selected} onSaved={refresh} />}
            {kind === "automations" && <AutomationFlow key={selected.id} workflow={selected} onSaved={refresh} />}
          </>
        )}
      </Drawer>

      {form && <RecordForm key={`${form}-${selected?.id ?? "new"}`} kind={kind} record={form === "edit" ? selected : undefined} open onClose={() => setForm(null)} onSave={save} />}
      {compose && (
        <CommunicationComposer
          key={`${compose}-${selected?.id ?? "new"}`}
          record={compose === "edit" ? selected : undefined}
          onClose={() => setCompose(null)}
          onSaved={async (item) => {
            await refresh();
            route.open(item.id);
          }}
        />
      )}
      <ConfirmDialog
        open={archiving !== null}
        onClose={() => setArchiving(null)}
        onConfirm={() => void archive()}
        title={archiving && archiving.length > 1 ? `Archive ${archiving.length} ${config.singular}s?` : `Archive this ${config.singular}?`}
        body="Archived records move to the recycle bin, where a manager or admin can restore them."
        confirmLabel="Archive"
      />
      <ConfirmDialog
        open={confirmUnsent}
        onClose={() => setConfirmUnsent(false)}
        onConfirm={() => void markUnsent()}
        title="Mark email as unsent?"
        body="Only continue if this message is absent from the connected Gmail account's Sent folder. This will allow a retry."
        confirmLabel="Mark unsent"
      />
    </Page>
  );
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-3">
      <dt className="shrink-0 text-ink-3">{label}</dt>
      <dd className="tnum min-w-0 truncate text-right font-medium">{children}</dd>
    </div>
  );
}

/** Addresses wrapped over several lines instead of cut off. */
function Addresses({ label, list }: { label: string; list: string[] }) {
  return (
    <div className="py-3">
      <dt className="text-ink-3">{label}</dt>
      <dd className="mt-1 leading-6 font-medium break-words">{list.join(", ")}</dd>
    </div>
  );
}
