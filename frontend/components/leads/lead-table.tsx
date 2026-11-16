"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { Avatar, StageBadge, buttonSecondary } from "@/components/ui/primitives";
import { formatDate } from "@/lib/format";
import type { Lead } from "@/lib/leads";
import { moneyValue } from "@/lib/money";
import { sourceLabel } from "@/lib/stages";
import { flexRender, getCoreRowModel, getSortedRowModel, useReactTable, type ColumnDef, type SortingState, type VisibilityState } from "@tanstack/react-table";
import { ArrowDown, ArrowUp, Check, Columns3, Minus, MoreHorizontal } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";

type Props = {
  rows: Lead[];
  selected: ReadonlyMap<string, Lead>;
  allOnPage: boolean;
  someOnPage: boolean;
  onToggle: (leads: Lead[], on: boolean) => void;
  onAct: (lead: Lead) => void;
};

function SelectionBox({ label, checked, mixed, onChange }: { label: string; checked: boolean; mixed?: boolean; onChange: (checked: boolean) => void }) {
  return <button type="button" role="checkbox" aria-label={label} aria-checked={mixed ? "mixed" : checked} onClick={() => onChange(!checked)} className={`grid size-4 place-items-center rounded border transition-colors ${checked || mixed ? "border-brand bg-brand text-white" : "border-line-strong bg-surface hover:border-brand"}`}>{mixed ? <Minus className="size-3" aria-hidden /> : checked ? <Check className="size-3" aria-hidden /> : null}</button>;
}

export function LeadTable({ rows, selected, allOnPage, someOnPage, onToggle, onAct }: Props) {
  const { format } = useCurrency();
  const [sorting, setSorting] = useState<SortingState>([]);
  const [visibility, setVisibility] = useState<VisibilityState>({});
  const columns = useMemo<ColumnDef<Lead>[]>(() => [
    { id: "select", header: () => <SelectionBox label="Select all leads on this page" checked={allOnPage} mixed={someOnPage} onChange={(checked) => onToggle(rows, checked)} />, cell: ({ row }) => <SelectionBox label={`Select ${row.original.name}`} checked={selected.has(row.original.id)} onChange={(checked) => onToggle([row.original], checked)} />, enableSorting: false, enableHiding: false, enableResizing: false, size: 44 },
    { id: "name", accessorFn: (lead) => lead.name, header: "Lead", cell: ({ row }) => <div className="flex min-w-0 items-center gap-3"><Avatar name={row.original.name} /><div className="min-w-0"><Link href={`/leads/${row.original.id}`} className="block truncate font-medium text-ink hover:text-brand hover:underline">{row.original.name}</Link><p className="truncate text-[12px] text-ink-3">{row.original.companyName || "No company"}</p></div></div>, size: 220 },
    { id: "contact", accessorFn: (lead) => lead.email ?? "", header: "Contact", cell: ({ row }) => <div className="min-w-0">{row.original.email && <a href={`mailto:${row.original.email}`} className="block truncate text-ink-2 hover:text-brand hover:underline">{row.original.email}</a>}{row.original.phone && <a href={`tel:${row.original.phone.replace(/[^+\d]/g, "")}`} className="tnum block truncate text-[12px] text-ink-3 hover:text-brand">{row.original.phone}</a>}</div>, size: 200 },
    { id: "source", accessorFn: (lead) => sourceLabel(lead.source), header: "Source", cell: ({ row }) => <span className="text-ink-2">{sourceLabel(row.original.source)}</span>, size: 110 },
    { id: "owner", accessorFn: (lead) => lead.owner?.name ?? "", header: "Owner", cell: ({ row }) => row.original.owner ? <span className="flex items-center gap-2 truncate text-ink-2"><Avatar name={row.original.owner.name} /><span className="truncate">{row.original.owner.name}</span></span> : <span className="text-ink-3">Unassigned</span>, size: 150 },
    { id: "status", accessorFn: (lead) => lead.status, header: "Stage", cell: ({ row }) => <StageBadge stage={row.original.status} />, size: 120 },
    { id: "estimatedValue", accessorFn: (lead) => Number(lead.estimatedValue?.amount ?? 0), header: "Est. value", cell: ({ row }) => <span className="tnum font-medium text-ink-2">{row.original.estimatedValue ? format(moneyValue(row.original.estimatedValue), row.original.estimatedValue.currency) : "—"}</span>, size: 150 },
    { id: "createdAt", accessorFn: (lead) => lead.createdAt, header: "Created", cell: ({ row }) => <time className="tnum text-ink-2" dateTime={row.original.createdAt}>{formatDate(row.original.createdAt)}</time>, size: 130 },
    { id: "actions", header: "Actions", cell: ({ row }) => <button aria-label={`Actions for ${row.original.name}`} onClick={() => onAct(row.original)} className="grid size-8 place-items-center rounded-md text-ink-3 hover:bg-canvas hover:text-ink"><MoreHorizontal className="size-4" aria-hidden /></button>, enableSorting: false, enableHiding: false, enableResizing: false, size: 56 },
  ], [allOnPage, someOnPage, onToggle, rows, selected, format, onAct]);
  const table = useReactTable({ data: rows, columns, state: { sorting, columnVisibility: visibility }, onSortingChange: setSorting, onColumnVisibilityChange: setVisibility, getCoreRowModel: getCoreRowModel(), getSortedRowModel: getSortedRowModel(), columnResizeMode: "onChange" });
  const sorted = table.getState().sorting;

  return <div>
    <div className="flex items-center justify-between border-t border-line px-4 py-2 text-[11.5px] text-ink-3"><span>Sort this page by a heading. Shift-click to add another column.{sorted.length ? ` ${sorted.length} active.` : ""}</span><details className="group relative"><summary className={buttonSecondary + " h-7 cursor-pointer list-none px-2 text-[11.5px]"}><Columns3 className="size-3.5" />Columns</summary><div className="absolute right-0 z-20 mt-1 min-w-44 rounded-lg border border-line bg-surface p-2 shadow-lg">{table.getAllLeafColumns().filter((column) => column.getCanHide()).map((column) => <label key={column.id} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-[12px] text-ink hover:bg-canvas"><input type="checkbox" checked={column.getIsVisible()} onChange={column.getToggleVisibilityHandler()} className="accent-brand" />{typeof column.columnDef.header === "string" ? column.columnDef.header : column.id}</label>)}</div></details></div>
    <div className="space-y-2 p-3 md:hidden">{table.getRowModel().rows.map(({ original: lead }) => <article key={lead.id} className="rounded-lg border border-line bg-surface p-3"><div className="flex items-start gap-3"><SelectionBox label={`Select ${lead.name}`} checked={selected.has(lead.id)} onChange={(checked) => onToggle([lead], checked)} /><Avatar name={lead.name} /><div className="min-w-0 flex-1"><Link href={`/leads/${lead.id}`} className="block truncate text-[13px] font-semibold text-ink">{lead.name}</Link><p className="truncate text-[12px] text-ink-3">{lead.companyName || "No company"}</p></div><button onClick={() => onAct(lead)} aria-label={`Actions for ${lead.name}`} className="text-ink-3"><MoreHorizontal className="size-4" /></button></div><div className="mt-3 flex items-center justify-between gap-2 border-t border-line pt-3 text-[12px]"><StageBadge stage={lead.status} /><span className="tnum text-ink-2">{lead.estimatedValue ? format(moneyValue(lead.estimatedValue), lead.estimatedValue.currency) : "No estimate"}</span></div></article>)}</div>
    <div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[980px] table-fixed text-left text-[12.5px]"><thead className="sticky top-0 z-10 bg-canvas text-[11px] uppercase tracking-wide text-ink-3"><tr>{table.getHeaderGroups().map((group) => group.headers.map((header) => <th key={header.id} style={{ width: header.getSize() }} className="relative border-y border-line px-3 py-2.5 font-medium"><div className="flex items-center justify-between gap-1">{header.column.getCanSort() ? <button type="button" onClick={(event) => header.column.toggleSorting(undefined, event.shiftKey)} className="inline-flex items-center gap-1 hover:text-ink" aria-label={`Sort by ${header.column.id}`}>{flexRender(header.column.columnDef.header, header.getContext())}{header.column.getIsSorted() === "asc" ? <ArrowUp className="size-3" /> : header.column.getIsSorted() === "desc" ? <ArrowDown className="size-3" /> : null}</button> : flexRender(header.column.columnDef.header, header.getContext())}</div>{header.column.getCanResize() && <div onMouseDown={header.getResizeHandler()} onTouchStart={header.getResizeHandler()} role="separator" aria-label={`Resize ${header.column.id} column`} aria-orientation="vertical" className={`absolute inset-y-0 right-0 w-1 cursor-col-resize touch-none hover:bg-brand ${header.column.getIsResizing() ? "bg-brand" : ""}`} />}</th>))}</tr></thead><tbody className="divide-y divide-line">{table.getRowModel().rows.map((row) => <tr key={row.id} className={selected.has(row.original.id) ? "bg-brand-soft/60" : "hover:bg-canvas/70"}>{row.getVisibleCells().map((cell) => <td key={cell.id} className="overflow-hidden px-3 py-2.5">{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}</tr>)}</tbody></table></div>
  </div>;
}
