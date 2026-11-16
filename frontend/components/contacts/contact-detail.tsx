"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { DeleteDialog } from "@/components/ui/delete-dialog";
import { EditDeleteActions } from "@/components/ui/icon-action";
import { Avatar, Card, StageBadge, buttonSecondary } from "@/components/ui/primitives";
import { useRefresh } from "@/hooks/use-refresh";
import { listActivities } from "@/lib/activities";
import type { Contact } from "@/lib/contacts";
import { deleteCompany, deleteContact } from "@/lib/contacts";
import { listDeals } from "@/lib/deals";
import { FOLLOW_UP_TYPE_LABELS, listFollowUps } from "@/lib/follow-ups";
import { cn, formatDay, formatTime, localIso } from "@/lib/format";
import { moneyValue } from "@/lib/money";
import type { Ref } from "@/lib/page";
import { AFTER, KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { Building2, Mail, Phone } from "lucide-react";
import { useState } from "react";
import { EditCompanyDialog } from "./edit-company-dialog";
import { EditContactDialog } from "./edit-contact-dialog";

export function ContactDetail({ contact: c }: { contact: Contact }) {
  const { format } = useCurrency();
  const refresh = useRefresh();
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState<Contact | null>(null);
  const [editingCompany, setEditingCompany] = useState<Ref | null>(null);
  const [deletingCompany, setDeletingCompany] = useState<Ref | null>(null);
  const deals = useQuery({ queryKey: [...KEYS.deals, "contact", c.id], queryFn: () => listDeals({ contactId: c.id, size: 10 }) });
  const followUps = useQuery({
    queryKey: [...KEYS.followUps, "contact", c.id],
    queryFn: () => listFollowUps({ contactId: c.id, status: "OPEN", size: 10 }),
  });
  const history = useQuery({ queryKey: [...KEYS.activities, "contact", c.id], queryFn: () => listActivities({ contactId: c.id, size: 8 }) });
  const facts = [
    ["Email", c.email ?? "—"],
    ["Phone", c.phone ?? "—"],
    ["Owner", c.owner.name],
  ];

  return (
    <Card>
      <div className="p-5">
        <div className="flex items-start gap-3.5">
          <Avatar name={c.name} size="md" />
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-[17px] font-semibold tracking-[-0.01em] text-ink">{c.name}</h2>
            <p className="truncate text-[13px] text-ink-2">
              {c.jobTitle ? `${c.jobTitle}${c.company ? " at " : ""}` : ""}
              {c.company?.name ?? (c.jobTitle ? "" : "No company")}
            </p>
          </div>
          <EditDeleteActions name={c.name} onEdit={() => setEditing(true)} onDelete={() => setDeleting(c)} />
        </div>
        {c.company && (
          <p className="mt-3 flex items-center gap-1 text-[12.5px] text-ink-3">
            <Building2 className="size-3.5" aria-hidden />
            {c.company.name}
            <button onClick={() => setEditingCompany(c.company)} className="ml-1 font-medium text-brand hover:underline">
              Edit
            </button>
            <button onClick={() => setDeletingCompany(c.company)} className="font-medium text-down hover:underline">
              Delete
            </button>
          </p>
        )}
        <div className="mt-4 grid grid-cols-2 gap-2">
          {c.email ? (
            <a href={`mailto:${c.email}`} className={buttonSecondary}>
              <Mail className="size-4" aria-hidden />
              Email
            </a>
          ) : (
            <span className={cn(buttonSecondary, "opacity-50")}>
              <Mail className="size-4" aria-hidden />
              Email
            </span>
          )}
          {c.phone ? (
            <a href={`tel:${c.phone.replace(/[^+\d]/g, "")}`} className={buttonSecondary}>
              <Phone className="size-4" aria-hidden />
              Call
            </a>
          ) : (
            <span className={cn(buttonSecondary, "opacity-50")}>
              <Phone className="size-4" aria-hidden />
              Call
            </span>
          )}
        </div>
      </div>

      <dl className="divide-y divide-line border-y border-line text-[13px]">
        {facts.map(([label, value]) => (
          <div key={label} className="flex items-baseline justify-between gap-4 px-5 py-2.5">
            <dt className="text-ink-3">{label}</dt>
            <dd className="tnum min-w-0 truncate font-medium text-ink">{value}</dd>
          </div>
        ))}
      </dl>

      <Section title="Deals" empty="No deals yet." loading={deals.isPending} count={deals.data?.items.length ?? 0}>
        {deals.data?.items.map((d) => (
          <li key={d.id} className="flex items-center justify-between gap-3">
            <span className="min-w-0 truncate text-ink">{d.name}</span>
            <span className="flex shrink-0 items-center gap-2">
              <span className="tnum text-ink-2">{format(moneyValue(d.amount), d.amount.currency)}</span>
              <StageBadge stage={d.stageCode} />
            </span>
          </li>
        ))}
      </Section>

      <Section title="Open follow-ups" empty="Nothing scheduled." loading={followUps.isPending} count={followUps.data?.items.length ?? 0}>
        {followUps.data?.items.map((f) => (
          <li key={f.id} className="flex items-baseline justify-between gap-3">
            <span className="min-w-0 truncate text-ink">
              {FOLLOW_UP_TYPE_LABELS[f.type]}: {f.title}
            </span>
            <span className="tnum shrink-0 text-ink-3">
              {formatDay(localIso(f.dueAt))}, {formatTime(localIso(f.dueAt))}
            </span>
          </li>
        ))}
      </Section>

      <Section title="Recent activity" empty="No activity logged yet." loading={history.isPending} count={history.data?.items.length ?? 0}>
        {history.data?.items.map((a) => (
          <li key={a.id}>
            <p className="flex items-baseline justify-between gap-3">
              <span className="min-w-0 truncate font-medium text-ink">{a.subject}</span>
              <span className="tnum shrink-0 text-ink-3">{formatDay(localIso(a.occurredAt))}</span>
            </p>
            {a.body && <p className="mt-0.5 text-ink-2">{a.body}</p>}
          </li>
        ))}
      </Section>

      <EditContactDialog contact={c} open={editing} onClose={() => setEditing(false)} />
      <DeleteDialog
        target={deleting}
        title={(x) => `Delete ${x.name}?`}
        body="The contact is removed. Their deals and activity stay, but are no longer linked to them."
        successMessage={(x) => `${x.name} deleted.`}
        action={(x) => deleteContact(x.id)}
        onClose={() => setDeleting(null)}
        onDeleted={() => void refresh(AFTER.contact)}
      />
      <EditCompanyDialog company={editingCompany} onClose={() => setEditingCompany(null)} />
      <DeleteDialog
        target={deletingCompany}
        title={(x) => `Delete ${x.name}?`}
        body="Only a company with no contacts and no deals can be deleted."
        successMessage={(x) => `${x.name} deleted.`}
        action={(x) => deleteCompany(x.id)}
        onClose={() => setDeletingCompany(null)}
        onDeleted={() => void refresh([...AFTER.contact, KEYS.companies])}
      />
    </Card>
  );
}

function Section({
  title,
  empty,
  count,
  loading,
  children,
}: {
  title: string;
  empty: string;
  count: number;
  loading?: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className="border-b border-line px-5 py-4 last:border-b-0">
      <h3 className="text-[12px] font-semibold tracking-wide text-ink-3 uppercase">{title}</h3>
      {loading ? (
        <p className="mt-2 text-[13px] text-ink-3">Loading…</p>
      ) : count === 0 ? (
        <p className="mt-2 text-[13px] text-ink-3">{empty}</p>
      ) : (
        <ul className="mt-2.5 space-y-2.5 text-[13px]">{children}</ul>
      )}
    </section>
  );
}
