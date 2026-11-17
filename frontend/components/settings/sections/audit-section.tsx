"use client";

import { Card, CardHeader, buttonSecondary } from "@/components/ui/primitives";
import { listAudit } from "@/lib/audit";
import { formatDate, localIso } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

/** Who changed what, newest first. */
export function AuditSection() {
  const [page, setPage] = useState(1);
  const log = useQuery({ queryKey: [...KEYS.audit, page], queryFn: () => listAudit({ page, size: 25 }) });
  const pageCount = Math.max(1, Math.ceil((log.data?.total ?? 0) / 25));

  return (
    <Card className="overflow-hidden">
      <CardHeader title="Audit log" subtitle="Every change to a lead, contact, company or deal, and who made it." />
      {log.isPending ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Loading…</p>
      ) : log.isError ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-down">Couldn&apos;t load the audit log.</p>
      ) : log.data.items.length === 0 ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Nothing has been recorded yet.</p>
      ) : (
        <ul className="divide-y divide-line border-t border-line">
          {log.data.items.map((entry) => (
            <li key={entry.id} className="px-5 py-3 text-[13px]">
              <p className="flex flex-wrap items-baseline gap-x-2">
                <span className="font-medium text-ink">{entry.actorName ?? "System"}</span>
                <span className="text-ink-2">
                  {entry.action.toLowerCase()}d a {entry.entityType.toLowerCase()}
                </span>
                <span className="tnum ml-auto text-[12px] text-ink-3">
                  {formatDate(entry.occurredAt)}, {localIso(entry.occurredAt).slice(11)}
                </span>
              </p>
              {entry.changes && (
                <ul className="mt-1 text-[12.5px] text-ink-3">
                  {Object.entries(entry.changes).map(([field, [from, to]]) => (
                    <li key={field}>
                      {field}: {String(from ?? "—")} → {String(to ?? "—")}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
      {pageCount > 1 && (
        <div className="flex items-center justify-between border-t border-line px-5 py-3 text-[12.5px] text-ink-3">
          <span>
            Page {page} of {pageCount}
          </span>
          <span className="flex gap-2">
            <button className={buttonSecondary} disabled={page === 1} onClick={() => setPage(page - 1)}>
              Newer
            </button>
            <button className={buttonSecondary} disabled={page >= pageCount} onClick={() => setPage(page + 1)}>
              Older
            </button>
          </span>
        </div>
      )}
    </Card>
  );
}
