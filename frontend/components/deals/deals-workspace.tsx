"use client";

import { useQuery } from "@tanstack/react-query";
import { flexRender, getCoreRowModel, getSortedRowModel, useReactTable, type ColumnDef, type SortingState } from "@tanstack/react-table";
import { ArrowRight, LayoutGrid, List, Plus, Search, SearchX } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { AddDealDialog } from "@/components/pipeline/add-deal-dialog";
import { useCurrency } from "@/components/currency/currency-provider";
import { Card, EmptyState, Page, PageHeader, StageBadge, Avatar, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { useDebounced } from "@/hooks/use-debounced";
import { type Deal, listDeals, listStages } from "@/lib/deals";
import { formatDay } from "@/lib/format";
import { moneyValue } from "@/lib/money";
import { KEYS } from "@/lib/query-keys";

export function DealsWorkspace() {
  const [query, setQuery] = useState("");
  const [stage, setStage] = useState("");
  const [page, setPage] = useState(1);
  const [adding, setAdding] = useState(false);
  const [sorting, setSorting] = useState<SortingState>([{ id: "expectedCloseDate", desc: false }]);
  const q = useDebounced(query.trim(), 200);
  const { format } = useCurrency();
  const stages = useQuery({ queryKey: KEYS.stages, queryFn: listStages });
  const deals = useQuery({ queryKey: [...KEYS.deals, "workspace", { q, stage, page }], queryFn: () => listDeals({ q, stage, page, size: 25 }) });
  const columns = useMemo<ColumnDef<Deal>[]>(() => [
    { accessorKey: "name", header: "Opportunity", cell: ({ row }) => <span className="block max-w-56 truncate font-medium text-ink">{row.original.name}</span> },
    { id: "company", accessorFn: (row) => row.company.name, header: "Company", cell: ({ row }) => <span className="text-ink-2">{row.original.company.name}</span> },
    { id: "amount", accessorFn: (row) => moneyValue(row.amount), header: "Value", cell: ({ row }) => <span className="tnum font-medium">{format(moneyValue(row.original.amount), row.original.amount.currency)}</span> },
    { id: "stage", accessorFn: (row) => row.stageCode, header: "Stage", cell: ({ row }) => <StageBadge stage={row.original.stageCode} /> },
    { id: "owner", accessorFn: (row) => row.owner.name, header: "Owner", cell: ({ row }) => <span className="flex items-center gap-2"><Avatar name={row.original.owner.name} />{row.original.owner.name}</span> },
    { accessorKey: "expectedCloseDate", header: "Close date", cell: ({ row }) => <span className="tnum text-ink-2">{row.original.expectedCloseDate ? formatDay(row.original.expectedCloseDate) : "—"}</span> },
  ], [format]);
  const table = useReactTable({ data: deals.data?.items ?? [], columns, state: { sorting }, onSortingChange: setSorting, getCoreRowModel: getCoreRowModel(), getSortedRowModel: getSortedRowModel() });
  const pages = Math.ceil((deals.data?.total ?? 0) / 25);

  return <Page>
    <PageHeader title="Deals" subtitle="Inspect and advance every opportunity from one workspace.">
      <Link href="/pipeline" className={buttonSecondary}><LayoutGrid className="size-4" />Pipeline</Link>
      <button className={buttonPrimary} onClick={() => setAdding(true)}><Plus className="size-4" />Add deal</button>
    </PageHeader>
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-center gap-2 border-b border-line p-3"><div className="relative min-w-56 flex-1 sm:max-w-sm"><Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" /><input value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="Search deals or companies" aria-label="Search deals" className="h-9 w-full rounded-lg border border-line bg-canvas pr-3 pl-9 text-[13px] focus:border-brand focus:outline-none" /></div><select className={`${control} px-3`} value={stage} onChange={(event) => { setStage(event.target.value); setPage(1); }} aria-label="Filter by stage"><option value="">All stages</option>{stages.data?.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}</select><span className="ml-auto hidden items-center gap-1 text-[12px] text-ink-3 sm:flex"><List className="size-4" />List view</span></div>
      {deals.isError ? <div className="p-8 text-[13px] text-down">Deals could not be loaded. <button onClick={() => deals.refetch()} className="underline">Retry</button></div> : deals.isPending ? <div className="space-y-2 p-4" role="status" aria-label="Loading deals">{Array.from({ length: 8 }, (_, i) => <div key={i} className="h-10 animate-pulse rounded-md bg-canvas motion-reduce:animate-none" />)}</div> : table.getRowModel().rows.length === 0 ? <EmptyState icon={SearchX} title={query || stage ? "No deals match your filters" : "No deals yet"} body="Try another search or create an opportunity.">{query || stage ? <button onClick={() => { setQuery(""); setStage(""); }} className={buttonSecondary}>Clear filters</button> : <button onClick={() => setAdding(true)} className={buttonPrimary}>Add deal</button>}</EmptyState> : <div className="overflow-x-auto"><table className="w-full min-w-[850px] text-left text-[12.5px]"><thead className="sticky top-0 bg-surface text-[11px] text-ink-3 uppercase"><tr>{table.getHeaderGroups().map((group) => group.headers.map((header) => <th key={header.id} className="border-b border-line px-4 py-3"><button onClick={header.column.getToggleSortingHandler()} className="inline-flex items-center gap-1 font-semibold hover:text-ink">{flexRender(header.column.columnDef.header, header.getContext())}<span aria-hidden>{header.column.getIsSorted() === "asc" ? "↑" : header.column.getIsSorted() === "desc" ? "↓" : ""}</span></button></th>))}<th className="border-b border-line px-4 py-3"><span className="sr-only">Open</span></th></tr></thead><tbody>{table.getRowModel().rows.map((row) => <tr key={row.id} className="hover:bg-canvas">{row.getVisibleCells().map((cell) => <td key={cell.id} className="border-b border-line px-4 py-3">{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}<td className="border-b border-line px-4 py-3"><Link href={`/deals/${row.original.id}`} aria-label={`Open ${row.original.name}`} className="grid size-7 place-items-center rounded-md text-ink-3 hover:bg-brand-soft hover:text-brand"><ArrowRight className="size-4" /></Link></td></tr>)}</tbody></table></div>}
      {pages > 1 && <div className="flex items-center justify-between border-t border-line px-4 py-3 text-[12px] text-ink-2"><span className="tnum">Page {page} of {pages} · {deals.data?.total} deals</span><div className="flex gap-2"><button className={buttonSecondary} disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>Previous</button><button className={buttonSecondary} disabled={page >= pages} onClick={() => setPage((value) => value + 1)}>Next</button></div></div>}
    </Card>
    <AddDealDialog open={adding} onClose={() => setAdding(false)} onAdded={() => deals.refetch()} />
  </Page>;
}
