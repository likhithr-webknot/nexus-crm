"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Download, Plus, Search, SearchX, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Badge, Card, EmptyState, Page, PageHeader, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { MODULES } from "./module-config";
import { RecordForm } from "./record-form";
import { QuoteBuilder } from "./quote-builder";
import { AutomationFlow } from "./automation-flow";
import { modulesApi, type ModuleRecord, type ModuleKind, type ModuleValues } from "@/lib/modules";
import { formatDay } from "@/lib/format";
import { useSession } from "@/components/auth/session";
import { api, errorMessage } from "@/lib/api";
import dynamic from "next/dynamic";
const CommunicationComposer = dynamic(() => import("./communication-composer").then((module) => module.CommunicationComposer), { ssr: false });

export function ModuleWorkspace({ kind, recordId }: { kind: ModuleKind; recordId?: string }) {
  const config = MODULES[kind];
  const role = useSession().role;
  const canWrite = kind === "products" || kind === "automations" ? role === "ADMIN" : kind === "campaigns" ? role !== "REP" : true;
  const router = useRouter();
  const client = useQueryClient();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [sort, setSort] = useState<"name" | "updatedAt">("updatedAt");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [form, setForm] = useState<"create" | "edit" | null>(null);
  const [compose, setCompose] = useState<"create" | "edit" | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [confirmUnsent, setConfirmUnsent] = useState(false);
  const records = useQuery({ queryKey: ["workspace", kind], queryFn: () => modulesApi.list(kind), staleTime: 30_000 });
  const all = records.data ?? [];
  const filtered = all.filter((record) => (!status || record.status === status) && (!query || `${record.name} ${record.account} ${record.owner}`.toLowerCase().includes(query.toLowerCase()))).sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : b.updatedAt.localeCompare(a.updatedAt));
  const selected = all.find((record) => record.id === recordId) ?? filtered[0];
  const refresh = () => client.invalidateQueries({ queryKey: ["workspace", kind] });
  const save = async (values: ModuleValues) => {
    if (form === "edit" && selected) await modulesApi.update(kind, selected.id, values);
    else await modulesApi.create(kind, values);
    await refresh();
    toast(`${config.singular[0].toUpperCase()}${config.singular.slice(1)} saved.`);
  };
  const archive = async () => {
    const ids = selectedIds.length ? selectedIds : selected ? [selected.id] : [];
    await modulesApi.archive(kind, ids);
    setSelectedIds([]);
    setConfirm(false);
    await refresh();
    toast(`${ids.length} ${ids.length === 1 ? config.singular : `${config.singular}s`} archived.`);
    if (recordId) router.push(`/${kind}`);
  };
  const markUnsent = async () => {
    if (!selected) return;
    try {
      await api(`/mail/mark-unsent/${selected.id}`, { method: "POST" });
      setConfirmUnsent(false);
      await refresh();
      toast("Marked as not sent. You can reopen the composer and retry.");
    } catch (cause) { toast(errorMessage(cause, "The email status could not be updated.")); }
  };
  const exportCsv = () => {
    const csv = ["Name,Status,Owner,Account,Value,Updated", ...filtered.map((record) => [record.name, record.status, record.owner, record.account, record.amount, record.updatedAt].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\r\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a"); anchor.href = url; anchor.download = `${kind}.csv`; anchor.click(); URL.revokeObjectURL(url);
    toast(`Exported ${filtered.length} records.`);
  };

  return <Page>
    <PageHeader title={config.title} subtitle={config.subtitle}><button onClick={exportCsv} disabled={!filtered.length} className={buttonSecondary}><Download className="size-4" />Export</button>{canWrite && <button onClick={() => kind === "communications" ? setCompose("create") : setForm("create")} className={buttonPrimary}><Plus className="size-4" />{kind === "communications" ? "Compose" : `New ${config.singular}`}</button>}</PageHeader>
    <div className="flex items-center gap-2 rounded-lg border border-brand/20 bg-brand-soft px-3 py-2 text-[11.5px] text-brand-ink"><span className="size-1.5 shrink-0 rounded-full bg-brand" />{config.note}</div>
    <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
      <Card className="overflow-hidden"><div className="flex flex-wrap items-center gap-2 border-b border-line p-3"><div className="relative min-w-44 flex-1"><Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Search ${config.title.toLowerCase()}`} aria-label={`Search ${config.title.toLowerCase()}`} className="h-9 w-full rounded-lg border border-line bg-canvas pr-3 pl-9 text-[13px] outline-none focus:border-brand" /></div><select value={status} onChange={(event) => setStatus(event.target.value)} className={`${control} px-2.5`} aria-label="Filter by status"><option value="">All statuses</option>{config.statuses.map((value) => <option key={value}>{value}</option>)}</select><select value={sort} onChange={(event) => setSort(event.target.value as "name" | "updatedAt")} className={`${control} px-2.5`} aria-label="Sort records"><option value="updatedAt">Recently updated</option><option value="name">Name</option></select></div>
        {canWrite && selectedIds.length > 0 && <div className="flex items-center justify-between border-b border-line bg-brand-soft px-4 py-2 text-[12px]"><span>{selectedIds.length} selected</span><button onClick={() => setConfirm(true)} className="inline-flex items-center gap-1 font-medium text-down"><Trash2 className="size-3.5" />Archive</button></div>}
        {records.isError ? <p className="p-6 text-[13px] text-down">Records could not be loaded. <button onClick={() => records.refetch()} className="underline">Retry</button></p> : records.isPending ? <div className="space-y-2 p-4" role="status" aria-label="Loading records">{Array.from({ length: 5 }, (_, i) => <div key={i} className="h-11 animate-pulse rounded-md bg-canvas motion-reduce:animate-none" />)}</div> : filtered.length === 0 ? <EmptyState icon={SearchX} title={query || status ? "No matching records" : `No ${config.title.toLowerCase()} yet`} body={query || status ? "Clear your filters to see everything." : `Create your first ${config.singular} to get started.`}>{query || status ? <button className={buttonSecondary} onClick={() => { setQuery(""); setStatus(""); }}>Clear filters</button> : canWrite ? <button className={buttonPrimary} onClick={() => kind === "communications" ? setCompose("create") : setForm("create")}>Create {config.singular}</button> : null}</EmptyState> : <div className="overflow-x-auto"><table className="w-full min-w-[550px] text-left text-[12.5px]"><thead className="bg-surface text-[11px] font-semibold text-ink-3 uppercase"><tr><th className="w-10 border-b border-line px-3 py-2">{canWrite && <input type="checkbox" aria-label="Select all visible records" checked={filtered.every((record) => selectedIds.includes(record.id))} onChange={(event) => setSelectedIds(event.target.checked ? filtered.map((record) => record.id) : [])} />}</th><th className="border-b border-line px-3 py-2">Name</th><th className="border-b border-line px-3 py-2">Status</th><th className="border-b border-line px-3 py-2">Owner</th><th className="border-b border-line px-3 py-2">Updated</th><th className="border-b border-line px-3 py-2"><span className="sr-only">Open</span></th></tr></thead><tbody>{filtered.map((record) => <tr key={record.id} className={selected?.id === record.id ? "bg-brand-soft/60" : "hover:bg-canvas"}><td className="border-b border-line px-3 py-3">{canWrite && <input type="checkbox" aria-label={`Select ${record.name}`} checked={selectedIds.includes(record.id)} onChange={(event) => setSelectedIds((ids) => event.target.checked ? [...ids, record.id] : ids.filter((id) => id !== record.id))} />}</td><td className="max-w-60 truncate border-b border-line px-3 py-3 font-medium">{record.name}<span className="mt-0.5 block truncate text-[11px] font-normal text-ink-3">{record.account || record.description}</span></td><td className="border-b border-line px-3 py-3"><Badge tone={record.status === "Active" || record.status === "Approved" || record.status === "Published" ? "green" : record.status === "Draft" ? "neutral" : "brand"}>{record.status}</Badge></td><td className="border-b border-line px-3 py-3 text-ink-2">{record.owner}</td><td className="tnum border-b border-line px-3 py-3 text-ink-3">{formatDay(record.updatedAt)}</td><td className="border-b border-line px-3 py-3"><Link href={`/${kind}/${record.id}`} aria-label={`Open ${record.name}`} className="text-brand"><ArrowRight className="size-4" /></Link></td></tr>)}</tbody></table></div>}
      </Card>
      <Card className="overflow-hidden">{selected ? <><div className="border-b border-line p-5"><p className="text-[11px] font-semibold text-ink-3 uppercase">{config.singular} detail</p><h2 className="mt-1 text-[17px] font-semibold">{selected.name}</h2><div className="mt-2"><Badge tone={selected.status === "Draft" ? "neutral" : "brand"}>{selected.status}</Badge></div></div><dl className="divide-y divide-line px-5 text-[12.5px]"><div className="flex justify-between gap-3 py-3"><dt className="text-ink-3">Owner</dt><dd className="font-medium">{selected.owner}</dd></div>{selected.account && <div className="flex justify-between gap-3 py-3"><dt className="text-ink-3">Account</dt><dd className="text-right font-medium">{selected.account}</dd></div>}{selected.recipient && <div className="flex justify-between gap-3 py-3"><dt className="text-ink-3">Recipient</dt><dd className="truncate font-medium">{selected.recipient}</dd></div>}{config.metric && <div className="flex justify-between gap-3 py-3"><dt className="text-ink-3">{config.metric}</dt><dd className="tnum font-medium">{kind === "automations" ? selected.amount : new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(selected.amount)}</dd></div>}<div className="py-3"><dt className="text-ink-3">Details</dt><dd className="mt-1 whitespace-pre-wrap leading-5">{selected.description || "No details added."}</dd></div></dl>{kind === "communications" && selected.status === "Unknown" && <div className="tone-amber border-t border-[var(--tone-line)] bg-[var(--tone-bg)] p-4 text-[12px] text-[var(--tone-ink)]"><p>Gmail did not confirm this send. Check the connected account’s Sent folder before retrying.</p><button onClick={() => setConfirmUnsent(true)} className="mt-2 font-semibold underline">Not in Sent? Mark as unsent</button></div>}{canWrite && <div className="flex gap-2 border-t border-line p-4">{(kind !== "communications" || selected.status === "Draft" || selected.status === "Failed") && <button onClick={() => kind === "communications" ? setCompose("edit") : setForm("edit")} className={buttonSecondary}>{kind === "communications" ? "Open composer" : "Edit"}</button>}<button onClick={() => setConfirm(true)} className={buttonSecondary}>Archive</button></div>}</> : <p className="p-6 text-[13px] text-ink-3">Select a record to see its details.</p>}</Card>
    </div>
    {kind === "quotes" && selected && <QuoteBuilder key={selected.id} quote={selected} onSaved={refresh} />}
    {kind === "automations" && selected && <AutomationFlow key={selected.id} workflow={selected} onSaved={refresh} />}
    {form && <RecordForm key={`${form}-${selected?.id ?? "new"}`} kind={kind} record={form === "edit" ? selected : undefined} open onClose={() => setForm(null)} onSave={save} />}
    {compose && <CommunicationComposer key={`${compose}-${selected?.id ?? "new"}`} record={compose === "edit" ? selected : undefined} onClose={() => setCompose(null)} onSaved={async (item) => { await refresh(); router.push(`/communications/${item.id}`); }} />}
    <ConfirmDialog open={confirm} onClose={() => setConfirm(false)} onConfirm={() => void archive()} title={selectedIds.length ? `Archive ${selectedIds.length} ${selectedIds.length === 1 ? config.singular : `${config.singular}s`}?` : `Archive this ${config.singular}?`} body="The selected records will be archived in the CRM." confirmLabel="Archive" />
    <ConfirmDialog open={confirmUnsent} onClose={() => setConfirmUnsent(false)} onConfirm={() => void markUnsent()} title="Mark email as unsent?" body="Only continue if this message is absent from the connected Gmail account’s Sent folder. This will allow a retry." confirmLabel="Mark unsent" />
  </Page>;
}
