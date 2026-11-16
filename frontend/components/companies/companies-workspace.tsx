"use client";

import { useQuery } from "@tanstack/react-query";
import { flexRender, getCoreRowModel, getSortedRowModel, useReactTable, type ColumnDef, type SortingState } from "@tanstack/react-table";
import { Building2, ExternalLink, Plus, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { EditCompanyDialog } from "@/components/contacts/edit-company-dialog";
import { useCurrency } from "@/components/currency/currency-provider";
import { Card, EmptyState, Page, PageHeader, StageBadge, Avatar, buttonPrimary } from "@/components/ui/primitives";
import { useDebounced } from "@/hooks/use-debounced";
import { getCompany, listCompanies, listContacts, type Company } from "@/lib/contacts";
import { listDeals } from "@/lib/deals";
import { moneyValue } from "@/lib/money";
import { KEYS } from "@/lib/query-keys";
import { ResearchCard } from "@/components/ai/research-card";

export function CompaniesWorkspace({ recordId }: { recordId?: string }) {
  const [query, setQuery] = useState("");
  const [sorting, setSorting] = useState<SortingState>([{ id: "name", desc: false }]);
  const [editing, setEditing] = useState(false);
  const search = useDebounced(query.trim(), 200);
  const router = useRouter();
  const { format } = useCurrency();
  const list = useQuery({ queryKey: [...KEYS.companies, "workspace", search], queryFn: () => listCompanies({ q: search, size: 100 }) });
  const selectedId = recordId ?? list.data?.items[0]?.id;
  const company = useQuery({ queryKey: [...KEYS.companies, "detail", selectedId], queryFn: () => getCompany(selectedId!), enabled: Boolean(selectedId) });
  const contacts = useQuery({ queryKey: [...KEYS.contacts, "company-workspace"], queryFn: () => listContacts({ size: 100 }), enabled: Boolean(selectedId) });
  const deals = useQuery({ queryKey: [...KEYS.deals, "company", selectedId], queryFn: () => listDeals({ companyId: selectedId, size: 50 }), enabled: Boolean(selectedId) });
  const relatedContacts = contacts.data?.items.filter((item) => item.company?.id === selectedId) ?? [];
  const openDeals = deals.data?.items.filter((deal) => !deal.closedAt) ?? [];
  const closedDeals = deals.data?.items.filter((deal) => deal.closedAt) ?? [];
  const columns = useMemo<ColumnDef<Company>[]>(() => [
    { accessorKey: "name", header: "Company", cell: ({ row }) => <span className="flex items-center gap-2.5 font-medium"><CompanyLogo url={row.original.logoUrl} /><span className="truncate">{row.original.name}</span></span> },
    { accessorKey: "industry", header: "Industry", cell: ({ row }) => row.original.industry ?? <span className="text-ink-3">—</span> },
    { accessorKey: "domain", header: "Domain", cell: ({ row }) => row.original.domain ?? <span className="text-ink-3">—</span> },
  ], []);
  const table = useReactTable({ data: list.data?.items ?? [], columns, state: { sorting }, onSortingChange: setSorting, getCoreRowModel: getCoreRowModel(), getSortedRowModel: getSortedRowModel() });

  return <Page>
    <PageHeader title="Companies" subtitle="The accounts and relationships behind every opportunity.">
      <button className={buttonPrimary} onClick={() => window.dispatchEvent(new CustomEvent("nexus:create", { detail: "company" }))}><Plus className="size-4" />Add company</button>
    </PageHeader>
    <div className="grid items-start gap-5 xl:grid-cols-[minmax(330px,42%)_minmax(0,1fr)]">
      <Card className="overflow-hidden">
        <div className="relative border-b border-line p-3"><Search className="absolute top-1/2 left-6 size-4 -translate-y-1/2 text-ink-3" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search companies" aria-label="Search companies" className="h-9 w-full rounded-lg border border-line bg-canvas pr-3 pl-9 text-[13px] outline-none focus:border-brand" /></div>
        {list.isError ? <div className="p-6 text-[13px] text-down">Companies could not be loaded. <button onClick={() => list.refetch()} className="underline">Retry</button></div> : list.isPending ? <div className="space-y-2 p-4" role="status" aria-label="Loading companies">{Array.from({ length: 6 }, (_, i) => <div key={i} className="h-10 animate-pulse rounded-md bg-canvas motion-reduce:animate-none" />)}</div> : table.getRowModel().rows.length === 0 ? <EmptyState icon={Building2} title={query ? "No matching companies" : "No companies yet"} body={query ? "Try a different name or clear the search." : "Create a company to start mapping your relationships."}>{query ? <button onClick={() => setQuery("")} className="text-brand">Clear search</button> : <button onClick={() => window.dispatchEvent(new CustomEvent("nexus:create", { detail: "company" }))} className="text-brand">Create company</button>}</EmptyState> : <div className="overflow-x-auto"><table className="w-full text-left text-[12.5px]"><thead className="sticky top-0 bg-surface text-[11px] text-ink-3 uppercase"><tr>{table.getHeaderGroups().map((group) => group.headers.map((header) => <th key={header.id} className="border-b border-line px-4 py-2.5"><button onClick={header.column.getToggleSortingHandler()} className="inline-flex items-center gap-1.5 font-medium hover:text-ink">{flexRender(header.column.columnDef.header, header.getContext())}<span aria-hidden>{header.column.getIsSorted() === "asc" ? "↑" : header.column.getIsSorted() === "desc" ? "↓" : ""}</span></button></th>))}</tr></thead><tbody>{table.getRowModel().rows.map((row) => <tr key={row.id} className={selectedId === row.original.id ? "bg-brand-soft" : "hover:bg-canvas"}>{row.getVisibleCells().map((cell) => <td key={cell.id} className="border-b border-line px-4 py-2.5"><button onClick={() => router.push(`/companies/${row.original.id}`)} className="w-full text-left text-ink">{flexRender(cell.column.columnDef.cell, cell.getContext())}</button></td>)}</tr>)}</tbody></table></div>}
      </Card>
      <div className="space-y-5">
        {company.isError ? <Card className="p-6 text-[13px] text-down">Account details could not be loaded. <button onClick={() => company.refetch()} className="underline">Retry</button></Card> : !company.data ? <Card className="h-64 animate-pulse motion-reduce:animate-none" /> : <>
          <Card>
            <div className="flex flex-wrap items-start gap-4 p-5"><CompanyLogo url={company.data.logoUrl} large /><div className="min-w-0 flex-1"><h2 className="text-[19px] font-semibold tracking-tight">{company.data.name}</h2><p className="mt-1 text-[13px] text-ink-2">{company.data.industry ?? "Industry not set"} · Owned by {company.data.owner.name}</p></div><button onClick={() => setEditing(true)} className="rounded-lg border border-line px-3 py-1.5 text-[12px] font-medium hover:bg-canvas">Edit account</button></div>
            <div className="grid gap-4 border-t border-line px-5 py-4 text-[12.5px] sm:grid-cols-2"><div><p className="text-ink-3">Website</p>{company.data.websiteUrl || company.data.domain ? <a href={company.data.websiteUrl ?? `https://${company.data.domain}`} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 font-medium text-brand hover:underline">{company.data.websiteUrl ?? company.data.domain}<ExternalLink className="size-3" /></a> : <p className="mt-1 text-ink-2">Not added</p>}</div><div><p className="text-ink-3">Account owner</p><p className="mt-1 font-medium">{company.data.owner.name}</p></div></div>
          </Card>
          <ResearchCard kind="company" id={company.data.id} has={{ website: Boolean(company.data.websiteUrl), logo: Boolean(company.data.logoUrl) }} onApplied={() => { void company.refetch(); void list.refetch(); }} />
          <Card><div className="flex items-center justify-between border-b border-line px-5 py-4"><div><h3 className="text-[15px] font-semibold">Relationship map</h3><p className="mt-0.5 text-[12px] text-ink-3">Key contacts at this account</p></div><span className="text-[12px] text-ink-3">{relatedContacts.length} contacts</span></div>{relatedContacts.length ? <div className="grid gap-2 p-4 sm:grid-cols-2">{relatedContacts.map((person) => <div key={person.id} className="flex items-center gap-2.5 rounded-lg border border-line p-3"><Avatar name={person.name} /><div className="min-w-0"><p className="truncate text-[13px] font-medium">{person.name}</p><p className="truncate text-[11px] text-ink-3">{person.jobTitle ?? "Contact"}</p></div></div>)}</div> : <p className="p-5 text-[13px] text-ink-3">No contacts are linked to this account yet.</p>}</Card>
          <Card><div className="flex items-center justify-between border-b border-line px-5 py-4"><h3 className="text-[15px] font-semibold">Opportunities</h3><span className="text-[12px] text-ink-3">{openDeals.length} open · {closedDeals.length} closed</span></div>{deals.isPending ? <p className="p-5 text-[13px] text-ink-3">Loading deals…</p> : deals.data?.items.length ? <ul className="divide-y divide-line">{deals.data.items.map((deal) => <li key={deal.id} className="flex items-center justify-between gap-3 px-5 py-3 text-[12.5px]"><div className="min-w-0"><p className="truncate font-medium">{deal.name}</p><p className="text-[11px] text-ink-3">{deal.owner.name}</p></div><div className="flex shrink-0 items-center gap-2"><span className="tnum">{format(moneyValue(deal.amount), deal.amount.currency)}</span><StageBadge stage={deal.stageCode} /></div></li>)}</ul> : <p className="p-5 text-[13px] text-ink-3">No opportunities linked to this account.</p>}</Card>
        </>}
      </div>
    </div>
    <EditCompanyDialog company={editing && company.data ? { id: company.data.id, name: company.data.name } : null} onClose={() => setEditing(false)} />
  </Page>;
}

/** The company's own logo on a white tile (logos are drawn for a light background), or a building icon when it has none. */
function CompanyLogo({ url, large }: { url: string | null; large?: boolean }) {
  const [failed, setFailed] = useState(false);
  const box = large ? "size-12 rounded-xl" : "size-7 rounded-lg";
  if (!url || failed) return <span className={`grid shrink-0 place-items-center bg-brand-soft text-brand ${box}`}><Building2 className={large ? "size-6" : "size-4"} aria-hidden /></span>;
  // eslint-disable-next-line @next/next/no-img-element -- a remote logo of unknown size and host
  return <span className={`grid shrink-0 place-items-center overflow-hidden border border-line bg-white ${box}`}><img src={url} alt="" referrerPolicy="no-referrer" onError={() => setFailed(true)} className={large ? "max-h-9 max-w-9 object-contain" : "max-h-5 max-w-5 object-contain"} /></span>;
}
