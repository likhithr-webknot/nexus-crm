"use client";

import { LoadFailed } from "@/components/ui/load-state";
import { formatDay, localIso } from "@/lib/format";
import { listEmails, type EmailOwner } from "@/lib/emails";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowUpRight, ExternalLink } from "lucide-react";
import Link from "next/link";

/**
 * Emails to and from a contact, lead or deal's main contact, copied there by inbox sync. Shows only a subject and a
 * short snippet, never the full message. The caller supplies the surrounding card.
 */
export function RecordEmails({ owner }: { owner: EmailOwner }) {
  const key = Object.entries(owner)[0];
  const emails = useQuery({ queryKey: [...KEYS.emails, ...key], queryFn: () => listEmails(owner) });

  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="text-[12px] font-semibold tracking-wide text-ink-3 uppercase">Emails</h3>
        <Link href="/settings?section=mail" className="text-[11.5px] font-medium text-brand hover:underline">
          Inbox sync settings
        </Link>
      </div>
      {emails.isPending ? (
        <p className="mt-2 text-[13px] text-ink-3">Loading emails…</p>
      ) : emails.isError ? (
        <LoadFailed what="emails" onRetry={() => emails.refetch()} />
      ) : emails.data.length === 0 ? (
        <p className="mt-2 text-[13px] text-ink-3">
          No emails yet. Mail to and from this person appears here once inbox sync is turned on in Settings.
        </p>
      ) : (
        <ul className="mt-2.5 divide-y divide-line">
          {emails.data.map((m) => (
            <li key={m.id} className="flex gap-3 py-3">
              <span
                className="mt-0.5 grid size-6 shrink-0 place-items-center rounded-full bg-canvas text-ink-2"
                title={m.direction === "IN" ? "Received" : "Sent"}
              >
                {m.direction === "IN" ? (
                  <ArrowDownLeft className="size-3.5" aria-label="Received" />
                ) : (
                  <ArrowUpRight className="size-3.5" aria-label="Sent" />
                )}
              </span>
              <div className="min-w-0 flex-1 text-[13px]">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="min-w-0 truncate font-medium text-ink">{m.subject}</p>
                  <time dateTime={m.sentAt} className="tnum shrink-0 text-[11.5px] text-ink-3">
                    {formatDay(localIso(m.sentAt))}
                  </time>
                </div>
                <p className="truncate text-[12px] text-ink-3">
                  {m.direction === "IN" ? `From ${m.from}` : `To ${m.to}`} · via {m.mailbox}
                </p>
                {m.snippet && <p className="mt-1 line-clamp-2 text-[12.5px] text-ink-2">{m.snippet}</p>}
                {m.gmailUrl && (
                  <a
                    href={m.gmailUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-1 inline-flex items-center gap-1 text-[12px] font-medium text-brand hover:underline"
                  >
                    Open in Gmail
                    <ExternalLink className="size-3" aria-hidden />
                  </a>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
