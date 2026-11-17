"use client";

import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useCurrency } from "@/components/currency/currency-provider";
import { TagList } from "@/components/tags/tag-chip";
import { Avatar, StageBadge } from "@/components/ui/primitives";
import { type Deal } from "@/lib/deals";
import { formatDay } from "@/lib/format";
import { moneyValue } from "@/lib/money";

/** The deals as a sortable table. A tick box on each row lets the page act on several deals at once. */
export function DealsTable({
  rows,
  ticked,
  onTick,
}: {
  rows: Deal[];
  ticked: ReadonlySet<string>;
  onTick: (id: string, on: boolean) => void;
}) {
  const { format } = useCurrency();
  const [sorting, setSorting] = useState<SortingState>([{ id: "expectedCloseDate", desc: false }]);

  const columns = useMemo<ColumnDef<Deal>[]>(
    () => [
      {
        id: "select",
        header: () => <span className="sr-only">Select</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <input
            type="checkbox"
            aria-label={`Select ${row.original.name}`}
            checked={ticked.has(row.original.id)}
            onChange={(event) => onTick(row.original.id, event.target.checked)}
            className="size-4 accent-brand"
          />
        ),
      },
      {
        accessorKey: "name",
        header: "Opportunity",
        cell: ({ row }) => <span className="block max-w-56 truncate font-medium text-ink">{row.original.name}</span>,
      },
      {
        id: "company",
        accessorFn: (row) => row.company.name,
        header: "Company",
        cell: ({ row }) => <span className="text-ink-2">{row.original.company.name}</span>,
      },
      {
        id: "amount",
        accessorFn: (row) => moneyValue(row.amount),
        header: "Value",
        cell: ({ row }) => (
          <span className="tnum font-medium">
            {format(moneyValue(row.original.amount), row.original.amount.currency)}
          </span>
        ),
      },
      {
        id: "stage",
        accessorFn: (row) => row.stageCode,
        header: "Stage",
        cell: ({ row }) => <StageBadge stage={row.original.stageCode} />,
      },
      {
        id: "owner",
        accessorFn: (row) => row.owner.name,
        header: "Owner",
        cell: ({ row }) => (
          <span className="flex items-center gap-2">
            <Avatar name={row.original.owner.name} />
            {row.original.owner.name}
          </span>
        ),
      },
      {
        id: "tags",
        accessorFn: (row) => (row.tags ?? []).map((t) => t.name).join(", "),
        header: "Tags",
        cell: ({ row }) =>
          (row.original.tags ?? []).length ? (
            <TagList tags={row.original.tags ?? []} />
          ) : (
            <span className="text-ink-3">—</span>
          ),
      },
      {
        accessorKey: "expectedCloseDate",
        header: "Close date",
        cell: ({ row }) => (
          <span className="tnum text-ink-2">
            {row.original.expectedCloseDate ? formatDay(row.original.expectedCloseDate) : "—"}
          </span>
        ),
      },
    ],
    [format, ticked, onTick],
  );
  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[980px] text-left text-[12.5px]">
        <thead className="sticky top-0 bg-surface text-[11px] text-ink-3 uppercase">
          <tr>
            {table.getHeaderGroups().map((group) =>
              group.headers.map((header) => (
                <th key={header.id} className="border-b border-line px-4 py-3">
                  <button
                    onClick={header.column.getToggleSortingHandler()}
                    className="inline-flex items-center gap-1 font-semibold hover:text-ink"
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    <span aria-hidden>
                      {header.column.getIsSorted() === "asc" ? "↑" : header.column.getIsSorted() === "desc" ? "↓" : ""}
                    </span>
                  </button>
                </th>
              )),
            )}
            <th className="border-b border-line px-4 py-3">
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr key={row.id} className="hover:bg-canvas">
              {row.getVisibleCells().map((cell) => (
                <td key={cell.id} className="border-b border-line px-4 py-3">
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
              <td className="border-b border-line px-4 py-3">
                <Link
                  href={`/deals/${row.original.id}`}
                  aria-label={`Open ${row.original.name}`}
                  className="grid size-7 place-items-center rounded-md text-ink-3 hover:bg-brand-soft hover:text-brand"
                >
                  <ArrowRight className="size-4" />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
