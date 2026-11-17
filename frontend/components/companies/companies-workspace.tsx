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
import { ArrowDown, ArrowUp, Building2, ExternalLink, Pencil, Plus, Search } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ResearchCard } from "@/components/ai/research-card";
import { EditCompanyDialog } from "@/components/contacts/edit-company-dialog";
import { useCurrency } from "@/components/currency/currency-provider";
import { Drawer } from "@/components/ui/drawer";
import { LoadFailed } from "@/components/ui/load-state";
import {
  Avatar,
  Card,
  CardHeader,
  EmptyState,
  Page,
  PageHeader,
  StageBadge,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { useDebounced } from "@/hooks/use-debounced";
import { useRecordRoute } from "@/hooks/use-record-route";
import { getCompany, listCompanies, listContacts, type Company } from "@/lib/contacts";
import { listDeals } from "@/lib/deals";
import { cn } from "@/lib/format";
import { moneyValue } from "@/lib/money";
import { KEYS } from "@/lib/query-keys";
import { openCreate } from "@/lib/shortcuts";

const dash = <span className="text-ink-3">—</span>;

/** Every company in a full-width table. Choosing one opens it in a panel on the right; the table stays put. */
export function CompaniesWorkspace() {
  const [query, setQuery] = useState("");
  const [sorting, setSorting] = useState<SortingState>([{ id: "name", desc: false }]);
  const search = useDebounced(query.trim(), 200);
  const route = useRecordRoute("/companies");
  const list = useQuery({
    queryKey: [...KEYS.companies, "workspace", search],
    queryFn: () => listCompanies({ q: search, size: 100 }),
  });

  const columns = useMemo<ColumnDef<Company>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Company",
        cell: ({ row }) => (
          <span className="flex items-center gap-2.5 font-medium text-ink">
            <CompanyLogo url={row.original.logoUrl} />
            <span className="truncate">{row.original.name}</span>
          </span>
        ),
      },
      { accessorKey: "industry", header: "Industry", cell: ({ row }) => row.original.industry ?? dash },
      {
        id: "website",
        accessorFn: (row) => row.websiteUrl ?? row.domain ?? "",
        header: "Website",
        cell: ({ getValue }) =>
          getValue<string>()
            ? getValue<string>()
                .replace(/^https?:\/\/(www\.)?/, "")
                .replace(/\/$/, "")
            : dash,
      },
      {
        id: "owner",
        accessorFn: (row) => row.owner.name,
        header: "Account owner",
        cell: ({ row }) => (
          <span className="flex items-center gap-2">
            <Avatar name={row.original.owner.name} />
            <span className="truncate">{row.original.owner.name}</span>
          </span>
        ),
      },
    ],
    [],
  );
  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table manages its own memoization
  const table = useReactTable({
    data: list.data?.items ?? [],
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });
  const rows = table.getRowModel().rows;

  return (
    <Page>
      <PageHeader title="Companies" subtitle="The accounts and relationships behind every opportunity.">
        <button className={buttonPrimary} onClick={() => openCreate("company")}>
          <Plus className="size-4" aria-hidden />
          Add company
        </button>
      </PageHeader>

      <Card className="overflow-hidden">
        <div className="flex items-center gap-3 border-b border-line p-3">
          <label className="relative min-w-0 flex-1 sm:max-w-sm">
            <span className="sr-only">Search companies</span>
            <Search
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3"
              aria-hidden
            />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search companies"
              className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
            />
          </label>
          {list.data && (
            <p className="tnum ml-auto text-[12.5px] text-ink-3" aria-live="polite">
              {list.data.total > rows.length
                ? `First ${rows.length} of ${list.data.total}`
                : `${list.data.total} ${list.data.total === 1 ? "company" : "companies"}`}
            </p>
          )}
        </div>

        {list.isError && !list.data ? (
          <LoadFailed what="companies" onRetry={() => list.refetch()} />
        ) : list.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading companies">
            {Array.from({ length: 6 }, (_, index) => (
              <div key={index} className="skeleton h-10" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <EmptyState
            icon={Building2}
            title={query ? "No matching companies" : "No companies yet"}
            body={
              query
                ? "Try a different name, or clear the search."
                : "Add a company to start mapping the people and deals around it."
            }
          >
            {query ? (
              <button onClick={() => setQuery("")} className={buttonSecondary}>
                Clear search
              </button>
            ) : (
              <button onClick={() => openCreate("company")} className={buttonPrimary}>
                Add company
              </button>
            )}
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-[13px]">
              <thead className="text-[11.5px] text-ink-3">
                <tr>
                  {table.getHeaderGroups()[0].headers.map((header) => {
                    const sorted = header.column.getIsSorted();
                    return (
                      <th
                        key={header.id}
                        aria-sort={sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : "none"}
                        className="border-b border-line px-4 py-2.5 font-medium"
                      >
                        <button
                          onClick={header.column.getToggleSortingHandler()}
                          className="inline-flex items-center gap-1 hover:text-ink"
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          {sorted === "asc" && <ArrowUp className="size-3" aria-hidden />}
                          {sorted === "desc" && <ArrowDown className="size-3" aria-hidden />}
                        </button>
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={row.id}
                    onClick={() => route.open(row.original.id)}
                    className={cn(
                      "cursor-pointer transition-colors duration-[var(--duration-hover)]",
                      route.id === row.original.id ? "bg-brand-soft/60" : "hover:bg-surface-muted",
                    )}
                  >
                    {row.getVisibleCells().map((cell, index) => (
                      <td key={cell.id} className="max-w-72 truncate border-b border-line px-4 py-2.5 text-ink-2">
                        {/* The first cell carries the keyboard and screen-reader way in; the whole row is the mouse target. */}
                        {index === 0 ? (
                          <button
                            onClick={() => route.open(row.original.id)}
                            aria-label={`Open ${row.original.name}`}
                            className="w-full rounded text-left"
                          >
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                          </button>
                        ) : (
                          flexRender(cell.column.columnDef.cell, cell.getContext())
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <CompanyDrawer id={route.id} onClose={route.close} onChanged={() => void list.refetch()} />
    </Page>
  );
}

function CompanyDrawer({ id, onClose, onChanged }: { id: string | null; onClose: () => void; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const { format } = useCurrency();
  const company = useQuery({
    queryKey: [...KEYS.companies, "detail", id],
    queryFn: () => getCompany(id!),
    enabled: Boolean(id),
  });
  const contacts = useQuery({
    queryKey: [...KEYS.contacts, "company-workspace"],
    queryFn: () => listContacts({ size: 100 }),
    enabled: Boolean(id),
  });
  const deals = useQuery({
    queryKey: [...KEYS.deals, "company", id],
    queryFn: () => listDeals({ companyId: id!, size: 50 }),
    enabled: Boolean(id),
  });
  const data = company.data;
  const people = contacts.data?.items.filter((person) => person.company?.id === id) ?? [];
  const open = deals.data?.items.filter((deal) => !deal.closedAt).length ?? 0;
  const website = data?.websiteUrl ?? (data?.domain ? `https://${data.domain}` : null);

  return (
    <Drawer
      open={Boolean(id)}
      onClose={onClose}
      size="lg"
      title={data?.name ?? "Company"}
      subtitle={data ? `${data.industry ?? "Industry not set"} · Owned by ${data.owner.name}` : undefined}
      actions={
        data && (
          <button onClick={() => setEditing(true)} className={buttonSecondary}>
            <Pencil className="size-3.5" aria-hidden />
            Edit
          </button>
        )
      }
    >
      {company.isError ? (
        <Card>
          <LoadFailed what="this company" onRetry={() => company.refetch()} />
        </Card>
      ) : !data ? (
        <div className="space-y-5" role="status" aria-label="Loading company">
          <div className="skeleton h-24" />
          <div className="skeleton h-40" />
          <div className="skeleton h-32" />
        </div>
      ) : (
        <>
          <Card className="flex items-center gap-4 p-5">
            <CompanyLogo url={data.logoUrl} large />
            <dl className="grid min-w-0 flex-1 gap-x-6 gap-y-3 text-[13px] sm:grid-cols-2">
              <div className="min-w-0">
                <dt className="text-[12px] text-ink-3">Website</dt>
                <dd className="mt-0.5 truncate font-medium">
                  {website ? (
                    <a
                      href={website}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex max-w-full items-center gap-1 text-brand hover:underline"
                    >
                      <span className="truncate">{website.replace(/^https?:\/\/(www\.)?/, "").replace(/\/$/, "")}</span>
                      <ExternalLink className="size-3 shrink-0" aria-hidden />
                    </a>
                  ) : (
                    <span className="font-normal text-ink-3">Not added</span>
                  )}
                </dd>
              </div>
              <div className="min-w-0">
                <dt className="text-[12px] text-ink-3">Account owner</dt>
                <dd className="mt-0.5 truncate font-medium">{data.owner.name}</dd>
              </div>
            </dl>
          </Card>

          <ResearchCard
            kind="company"
            id={data.id}
            has={{ website: Boolean(data.websiteUrl), logo: Boolean(data.logoUrl) }}
            onApplied={() => {
              void company.refetch();
              onChanged();
            }}
          />

          <Card>
            <CardHeader
              title="People"
              subtitle="Contacts at this account"
              action={<span className="tnum text-[12.5px] text-ink-3">{people.length}</span>}
            />
            {people.length ? (
              <ul className="grid gap-2 border-t border-line p-4 sm:grid-cols-2">
                {people.map((person) => (
                  <li key={person.id}>
                    <Link
                      href={`/contacts/${person.id}`}
                      className="flex items-center gap-2.5 rounded-lg border border-line p-2.5 hover:border-line-strong hover:bg-surface-muted"
                    >
                      <Avatar name={person.name} />
                      <span className="min-w-0">
                        <span className="block truncate text-[13px] font-medium">{person.name}</span>
                        <span className="block truncate text-[12px] text-ink-3">{person.jobTitle ?? "Contact"}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="border-t border-line p-5 text-[13px] text-ink-3">
                {contacts.isPending ? "Loading…" : "No contacts are linked to this account yet."}
              </p>
            )}
          </Card>

          <Card>
            <CardHeader
              title="Opportunities"
              action={
                deals.data && (
                  <span className="tnum text-[12.5px] text-ink-3">
                    {open} open · {deals.data.items.length - open} closed
                  </span>
                )
              }
            />
            {deals.isError ? (
              <p className="border-t border-line p-5 text-[13px] text-down">
                Deals could not be loaded.{" "}
                <button onClick={() => void deals.refetch()} className="underline">
                  Retry
                </button>
              </p>
            ) : deals.data?.items.length ? (
              <ul className="divide-y divide-line border-t border-line">
                {deals.data.items.map((deal) => (
                  <li key={deal.id}>
                    <Link
                      href={`/deals/${deal.id}`}
                      className="flex items-center justify-between gap-3 px-5 py-3 text-[13px] hover:bg-surface-muted"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-medium">{deal.name}</span>
                        <span className="block text-[12px] text-ink-3">{deal.owner.name}</span>
                      </span>
                      <span className="flex shrink-0 items-center gap-2.5">
                        <span className="tnum">{format(moneyValue(deal.amount), deal.amount.currency)}</span>
                        <StageBadge stage={deal.stageCode} />
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="border-t border-line p-5 text-[13px] text-ink-3">
                {deals.isPending ? "Loading…" : "No opportunities are linked to this account."}
              </p>
            )}
          </Card>
          {/* Inside the drawer, so the keyboard stays with the form while it is open. */}
          <EditCompanyDialog
            company={editing ? { id: data.id, name: data.name } : null}
            onClose={() => {
              setEditing(false);
              void company.refetch();
              onChanged();
            }}
          />
        </>
      )}
    </Drawer>
  );
}

/** The company's own logo on a white tile (logos are drawn for a light background), or a building icon when it has none. */
function CompanyLogo({ url, large }: { url: string | null; large?: boolean }) {
  const [failed, setFailed] = useState(false);
  const box = large ? "size-14 rounded-xl" : "size-7 rounded-lg";
  if (!url || failed) {
    return (
      <span className={cn("grid shrink-0 place-items-center bg-brand-soft text-brand", box)}>
        <Building2 className={large ? "size-6" : "size-4"} aria-hidden />
      </span>
    );
  }
  return (
    <span className={cn("grid shrink-0 place-items-center overflow-hidden border border-line bg-white", box)}>
      {/* eslint-disable-next-line @next/next/no-img-element -- a remote logo of unknown size and host */}
      <img
        src={url}
        alt=""
        referrerPolicy="no-referrer"
        onError={() => setFailed(true)}
        className={large ? "max-h-10 max-w-10 object-contain" : "max-h-5 max-w-5 object-contain"}
      />
    </span>
  );
}
