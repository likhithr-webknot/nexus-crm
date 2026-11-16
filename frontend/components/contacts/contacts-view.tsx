"use client";

import { useRef, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Mail, Phone, Plus, Search, SearchX, WifiOff } from "lucide-react";
import {
  Avatar,
  Card,
  Field,
  EmptyState,
  Page,
  PageHeader,
  StageBadge,
  buttonPrimary,
  buttonSecondary,
  control,
  fieldControl,
} from "@/components/ui/primitives";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { cn, formatDay, formatTime, localIso, readForm, splitName } from "@/lib/format";
import { useCurrency } from "@/components/currency/currency-provider";
import { errorMessage } from "@/lib/api";
import { createContact, listContacts, type Contact } from "@/lib/contacts";
import { listDeals } from "@/lib/deals";
import { listFollowUps, FOLLOW_UP_TYPE_LABELS } from "@/lib/follow-ups";
import { listActivities } from "@/lib/activities";
import { moneyValue } from "@/lib/money";
import { listAssignable } from "@/lib/users";
import { useSession } from "@/components/auth/session";

export function ContactsView() {
  const [adding, setAdding] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | undefined>();
  const detailRef = useRef<HTMLDivElement>(null);

  const q = query.trim();
  const list = useQuery({
    queryKey: ["contacts", q],
    queryFn: () => listContacts({ q, size: 100 }),
    placeholderData: keepPreviousData,
  });
  const contacts = list.data?.items ?? [];
  const selected = contacts.find((c) => c.id === selectedId) ?? contacts[0];

  const select = (id: string) => {
    setSelectedId(id);
    // On a phone the details sit below the list, so bring them into view.
    requestAnimationFrame(() => detailRef.current?.scrollIntoView({ block: "nearest", behavior: "smooth" }));
  };

  return (
    <Page>
      <PageHeader title="Contacts" subtitle="Everyone you sell to, with their deals, follow-ups and history in one place.">
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          Add contact
        </button>
      </PageHeader>

      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_400px]">
        <Card>
          <div className="flex items-center gap-3 p-4">
            <label className="relative flex-1 sm:max-w-xs">
              <span className="sr-only">Search contacts</span>
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search name, company, email…"
                className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
              />
            </label>
            <p className="tnum ml-auto text-[12.5px] text-ink-3" aria-live="polite">
              {list.data ? (list.data.total > contacts.length ? `First ${contacts.length} of ${list.data.total}` : `${list.data.total} contacts`) : ""}
            </p>
          </div>

          {list.isError && !list.data ? (
            <div className="border-t border-line">
              <EmptyState icon={WifiOff} title="Couldn't load contacts" body="Check your connection, then try again.">
                <button onClick={() => list.refetch()} className={buttonSecondary}>
                  Try again
                </button>
              </EmptyState>
            </div>
          ) : list.isPending ? (
            <p className="border-t border-line px-5 py-10 text-center text-[13px] text-ink-3" role="status">
              Loading contacts…
            </p>
          ) : contacts.length === 0 ? (
            <div className="border-t border-line">
              <EmptyState
                icon={SearchX}
                title={q ? "No contacts found" : "No contacts yet"}
                body={q ? "Check the spelling, or search by company or email instead." : "Add a contact, or convert a lead to create one."}
              >
                {q ? (
                  <button onClick={() => setQuery("")} className={buttonSecondary}>
                    Clear search
                  </button>
                ) : null}
              </EmptyState>
            </div>
          ) : (
            <ul className="divide-y divide-line border-t border-line">
              {contacts.map((c) => (
                <li key={c.id}>
                  <button
                    onClick={() => select(c.id)}
                    aria-current={c.id === selected?.id ? "true" : undefined}
                    className={cn(
                      "flex w-full items-center gap-3 px-4 py-3 text-left transition-colors",
                      c.id === selected?.id ? "bg-brand-soft/60" : "hover:bg-canvas/70",
                    )}
                  >
                    <Avatar name={c.name} size="md" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[13.5px] font-medium text-ink">{c.name}</span>
                      <span className="block truncate text-[12.5px] text-ink-3">
                        {[c.jobTitle, c.company?.name].filter(Boolean).join(" · ") || "—"}
                      </span>
                    </span>
                    <span className="hidden w-60 shrink-0 truncate text-[12.5px] text-ink-2 md:block">{c.email}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <div ref={detailRef} className="lg:sticky lg:top-6">
          {selected ? <ContactDetail contact={selected} /> : null}
        </div>
      </div>

      <AddContactDialog open={adding} onClose={() => setAdding(false)} onAdded={(c) => select(c.id)} />
    </Page>
  );
}

function ContactDetail({ contact: c }: { contact: Contact }) {
  const { format } = useCurrency();
  const deals = useQuery({ queryKey: ["deals", "contact", c.id], queryFn: () => listDeals({ contactId: c.id, size: 10 }) });
  const followUps = useQuery({
    queryKey: ["follow-ups", "contact", c.id],
    queryFn: () => listFollowUps({ contactId: c.id, status: "OPEN", size: 10 }),
  });
  const history = useQuery({ queryKey: ["activities", "contact", c.id], queryFn: () => listActivities({ contactId: c.id, size: 8 }) });
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
        </div>
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

function AddContactDialog({ open, onClose, onAdded }: { open: boolean; onClose: () => void; onAdded: (contact: Contact) => void }) {
  const queryClient = useQueryClient();
  const session = useSession();
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable, enabled: open });
  const [error, setError] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: createContact,
    onSuccess: (contact) => {
      toast(`${contact.name} added.`);
      void queryClient.invalidateQueries({ queryKey: ["contacts"] });
      onAdded(contact);
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    setError(null);
    add.mutate({
      ...splitName(f.name),
      jobTitle: f.title || undefined,
      companyName: f.company || undefined,
      ownerId: f.owner || undefined,
      email: f.email || undefined,
      phone: f.phone || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} title="Add contact">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Full name">
            <input name="name" required maxLength={240} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Job title" optional>
            <input name="title" maxLength={120} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Company" optional>
            <input name="company" maxLength={160} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Owner">
            <select name="owner" defaultValue={session.id} className={fieldControl}>
              {(people.data ?? [{ id: session.id, displayName: session.displayName }]).map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Email">
            <input name="email" type="email" required maxLength={254} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Phone" optional>
            <input name="phone" type="tel" maxLength={40} autoComplete="off" className={fieldControl} />
          </Field>
          {error && (
            <div className="sm:col-span-2">
              <FormError>{error}</FormError>
            </div>
          )}
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Add contact" pending={add.isPending} />
      </form>
    </Dialog>
  );
}
