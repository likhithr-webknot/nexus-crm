"use client";

import { useQuery } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowRight, LayoutGrid, List, Plus, Search, SearchX } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { AddDealDialog } from "@/components/pipeline/add-deal-dialog";
import { useCurrency } from "@/components/currency/currency-provider";
import {
  Card,
  EmptyState,
  Page,
  PageHeader,
  StageBadge,
  Avatar,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { SavedViewsMenu } from "@/components/saved-views/saved-views-menu";
import { TagList } from "@/components/tags/tag-chip";
import { RecordBulkBar } from "@/components/tags/record-bulk-bar";
import { useDebounced } from "@/hooks/use-debounced";
import { useTags } from "@/hooks/use-tags";
import { type Deal, listDeals, listStages } from "@/lib/deals";
import { formatDay } from "@/lib/format";
import { moneyValue } from "@/lib/money";
import { KEYS } from "@/lib/query-keys";

export function DealsWorkspace() {
  const [query, setQuery] = useState("");
  const [stage, setStage] = useState("");
  const [tag, setTag] = useState("");
  const [ticked, setTicked] = useState<ReadonlySet<string>>(new Set());
  const [page, setPage] = useState(1);
  const [adding, setAdding] = useState(false);
  const [sorting, setSorting] = useState<SortingState>([{ id: "expectedCloseDate", desc: false }]);
  const q = useDebounced(query.trim(), 200);
  const { format } = useCurrency();
  const tags = useTags();
  const stages = useQuery({ queryKey: KEYS.stages, queryFn: listStages });
  const deals = useQuery({
    queryKey: [...KEYS.deals, "workspace", { q, stage, tag, page }],
    queryFn: () => listDeals({ q, stage, tag, page, size: 25 }),
  });
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
            onChange={(event) =>
              setTicked((prev) => {
                const next = new Set(prev);
                if (event.target.checked) next.add(row.original.id);
                else next.delete(row.original.id);
                return next;
              })
            }
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
    [format, ticked],
  );
  const table = useReactTable({
    data: deals.data?.items ?? [],
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });
  const pages = Math.ceil((deals.data?.total ?? 0) / 25);

  return (
    <Page>
      <PageHeader title="Deals" subtitle="Inspect and advance every opportunity from one workspace.">
        <Link href="/pipeline" className={buttonSecondary}>
          <LayoutGrid className="size-4" />
          Pipeline
        </Link>
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" />
          Add deal
        </button>
      </PageHeader>
      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 border-b border-line p-3">
          <div className="relative min-w-56 flex-1 sm:max-w-sm">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
            <input
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(1);
              }}
              placeholder="Search deals or companies"
              aria-label="Search deals"
              className="h-9 w-full rounded-lg border border-line bg-canvas pr-3 pl-9 text-[13px] focus:border-brand focus:outline-none"
            />
          </div>
          <select
            className={`${control} px-3`}
            value={stage}
            onChange={(event) => {
              setStage(event.target.value);
              setPage(1);
            }}
            aria-label="Filter by stage"
          >
            <option value="">All stages</option>
            {stages.data?.map((item) => (
              <option key={item.code} value={item.code}>
                {item.name}
              </option>
            ))}
          </select>
          {tags.data && tags.data.length > 0 && (
            <select
              className={`${control} px-3`}
              value={tag}
              onChange={(event) => {
                setTag(event.target.value);
                setPage(1);
              }}
              aria-label="Filter by tag"
            >
              <option value="">All tags</option>
              {tags.data.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          )}
          <SavedViewsMenu
            kind="deal"
            current={{ q: query.trim(), stage, tag }}
            onApply={(f) => {
              setQuery(f.q ?? "");
              setStage(f.stage ?? "");
              setTag(f.tag ?? "");
              setPage(1);
            }}
          />
          <span className="ml-auto hidden items-center gap-1 text-[12px] text-ink-3 sm:flex">
            <List className="size-4" />
            List view
          </span>
        </div>
        {ticked.size > 0 && <RecordBulkBar kind="deals" ids={[...ticked]} onClear={() => setTicked(new Set())} />}
        {deals.isError ? (
          <div className="p-8 text-[13px] text-down">
            Deals could not be loaded.{" "}
            <button onClick={() => deals.refetch()} className="underline">
              Retry
            </button>
          </div>
        ) : deals.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading deals">
            {Array.from({ length: 8 }, (_, i) => (
              <div key={i} className="h-10 rounded-md skeleton" />
            ))}
          </div>
        ) : table.getRowModel().rows.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title={query || stage || tag ? "No deals match your filters" : "No deals yet"}
            body="Try another search or create an opportunity."
          >
            {query || stage || tag ? (
              <button
                onClick={() => {
                  setQuery("");
                  setStage("");
                  setTag("");
                }}
                className={buttonSecondary}
              >
                Clear filters
              </button>
            ) : (
              <button onClick={() => setAdding(true)} className={buttonPrimary}>
                Add deal
              </button>
            )}
          </EmptyState>
        ) : (
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
                            {header.column.getIsSorted() === "asc"
                              ? "↑"
                              : header.column.getIsSorted() === "desc"
                                ? "↓"
                                : ""}
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
        )}
        {pages > 1 && (
          <div className="flex items-center justify-between border-t border-line px-4 py-3 text-[12px] text-ink-2">
            <span className="tnum">
              Page {page} of {pages} · {deals.data?.total} deals
            </span>
            <div className="flex gap-2">
              <button className={buttonSecondary} disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
                Previous
              </button>
              <button
                className={buttonSecondary}
                disabled={page >= pages}
                onClick={() => setPage((value) => value + 1)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </Card>
      <AddDealDialog open={adding} onClose={() => setAdding(false)} onAdded={() => deals.refetch()} />
    </Page>
  );
}
