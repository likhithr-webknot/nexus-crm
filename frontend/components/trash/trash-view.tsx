"use client";

import { useSession } from "@/components/auth/session";
import { LoadFailed, LoadingNote } from "@/components/ui/load-state";
import { Badge, Card, EmptyState, Page, PageHeader, buttonSecondary, control } from "@/components/ui/primitives";
import { cn, formatDate } from "@/lib/format";
import { TRASH_LABELS, type TrashType } from "@/lib/trash";
import { Lock, Trash2, Undo2 } from "lucide-react";
import { useState } from "react";
import { useRestore, useTrash } from "./use-trash";

const TYPES = Object.keys(TRASH_LABELS) as TrashType[];

/** What was deleted, newest first, with a button to bring each record back. */
export function TrashView() {
  const role = useSession().role;
  const [type, setType] = useState<TrashType | "">("");
  const trash = useTrash(type);
  const restore = useRestore();
  const items = trash.data?.items ?? [];
  const total = trash.data?.total ?? 0;
  const days = trash.data?.retentionDays ?? 30;

  // The server refuses reps as well. This only gives them a clear message instead of a failed load.
  if (role === "REP") {
    return (
      <Page>
        <PageHeader title="Recycle bin" subtitle="Deleted records can be restored here." />
        <Card>
          <EmptyState
            icon={Lock}
            title="You don't have access"
            body="Only managers and admins can restore deleted records. Ask one of them if something was deleted by mistake."
          />
        </Card>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader title="Recycle bin" subtitle="Restore deleted records before they are removed for good.">
        <select
          aria-label="Type"
          value={type}
          onChange={(e) => setType(e.target.value as TrashType | "")}
          className={cn(control, "px-2.5")}
        >
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {TRASH_LABELS[t]}s
            </option>
          ))}
        </select>
      </PageHeader>

      <Card>
        {trash.isError && !trash.data ? (
          <LoadFailed what="the recycle bin" onRetry={() => trash.refetch()} />
        ) : trash.isPending ? (
          <LoadingNote what="deleted records" />
        ) : items.length === 0 ? (
          <EmptyState
            icon={Trash2}
            title="Nothing here"
            body="Records you delete show up here, so a mistake can be undone."
          />
        ) : (
          <ul className="divide-y divide-line">
            {items.map((item) => (
              <li key={`${item.type}-${item.id}`} className="flex items-center gap-3 px-5 py-3 text-[13px]">
                <span className="w-32 shrink-0">
                  <Badge>{TRASH_LABELS[item.type]}</Badge>
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-ink">{item.name}</p>
                  <p className="truncate text-[12.5px] text-ink-3">
                    Deleted {formatDate(item.deletedAt)}
                    {item.ownerName ? ` · owned by ${item.ownerName}` : " · unassigned"}
                  </p>
                </div>
                <button
                  className={buttonSecondary}
                  disabled={restore.isPending}
                  onClick={() => restore.mutate(item)}
                  aria-label={`Restore ${item.name}`}
                >
                  <Undo2 className="size-4" aria-hidden />
                  Restore
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <p className="text-[12.5px] text-ink-3">
        {total > items.length
          ? `Showing the ${items.length} most recently deleted of ${total} records. Pick a type above to narrow the list. `
          : ""}
        Deleted records are removed for good {days} days after they were deleted.
      </p>
    </Page>
  );
}
