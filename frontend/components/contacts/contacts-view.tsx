"use client";

import { useRef, useState } from "react";
import { Mail, Phone, Plus, Search, SearchX } from "lucide-react";
import { ACTIVITIES, FOLLOW_UPS, type Contact } from "@/lib/mock-data";
import {
  Avatar,
  Card,
  EmptyState,
  NEEDS_API,
  Page,
  PageHeader,
  StageBadge,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { cn, formatDay, formatTime, formatUsd } from "@/lib/format";

export function ContactsView({ contacts }: { contacts: Contact[] }) {
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState(contacts[0]?.id);
  const detailRef = useRef<HTMLDivElement>(null);

  const q = query.trim().toLowerCase();
  const shown = contacts.filter((c) => !q || [c.name, c.company, c.email, c.title].some((f) => f.toLowerCase().includes(q)));
  const selected = contacts.find((c) => c.id === selectedId);

  const select = (id: string) => {
    setSelectedId(id);
    // On a phone the details sit below the list, so bring them into view.
    requestAnimationFrame(() => detailRef.current?.scrollIntoView({ block: "nearest", behavior: "smooth" }));
  };

  return (
    <Page>
      <PageHeader title="Contacts" subtitle="Everyone you sell to, with their deals, follow-ups and history in one place.">
        <button className={buttonPrimary} disabled title={NEEDS_API}>
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
              {shown.length} of {contacts.length}
            </p>
          </div>

          {shown.length === 0 ? (
            <div className="border-t border-line">
              <EmptyState icon={SearchX} title="No contacts found" body="Check the spelling, or search by company or email instead.">
                <button onClick={() => setQuery("")} className={buttonSecondary}>
                  Clear search
                </button>
              </EmptyState>
            </div>
          ) : (
            <ul className="divide-y divide-line border-t border-line">
              {shown.map((c) => (
                <li key={c.id}>
                  <button
                    onClick={() => select(c.id)}
                    aria-current={c.id === selectedId ? "true" : undefined}
                    className={cn(
                      "flex w-full items-center gap-3 px-4 py-3 text-left transition-colors",
                      c.id === selectedId ? "bg-brand-soft/60" : "hover:bg-canvas/70",
                    )}
                  >
                    <Avatar name={c.name} size="md" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[13.5px] font-medium text-ink">{c.name}</span>
                      <span className="block truncate text-[12.5px] text-ink-3">
                        {c.title} · {c.company}
                      </span>
                    </span>
                    <span className="hidden w-60 shrink-0 truncate text-[12.5px] text-ink-2 md:block">{c.email}</span>
                    <span className="flex w-24 shrink-0 justify-end">
                      <StageBadge stage={c.stage} />
                    </span>
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
    </Page>
  );
}

function ContactDetail({ contact: c }: { contact: Contact }) {
  const followUps = FOLLOW_UPS.filter((f) => f.contact === c.name && !f.done);
  const history = ACTIVITIES.filter((a) => a.contact === c.name);
  const facts = [
    ["Email", c.email],
    ["Phone", c.phone],
    ["Owner", c.owner ?? "Unassigned"],
    ["Deal value", formatUsd(c.value)],
  ];

  return (
    <Card>
      <div className="p-5">
        <div className="flex items-start gap-3.5">
          <span className="grid size-12 shrink-0 place-items-center rounded-full bg-brand-soft text-[15px] font-semibold text-brand-ink">
            {c.name
              .split(" ")
              .map((p) => p[0])
              .join("")}
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-[17px] font-semibold tracking-[-0.01em] text-ink">{c.name}</h2>
            <p className="truncate text-[13px] text-ink-2">
              {c.title} at {c.company}
            </p>
            <div className="mt-2">
              <StageBadge stage={c.stage} />
            </div>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2">
          <a href={`mailto:${c.email}`} className={buttonSecondary}>
            <Mail className="size-4" aria-hidden />
            Email
          </a>
          <a href={`tel:${c.phone.replace(/[^+\d]/g, "")}`} className={buttonSecondary}>
            <Phone className="size-4" aria-hidden />
            Call
          </a>
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

      <Section title="Open follow-ups" empty="Nothing scheduled." count={followUps.length}>
        {followUps.map((f) => (
          <li key={f.id} className="flex items-baseline justify-between gap-3">
            <span className="min-w-0 truncate text-ink">
              {f.type}: {f.title}
            </span>
            <span className="tnum shrink-0 text-ink-3">
              {formatDay(f.dueAt)}, {formatTime(f.dueAt)}
            </span>
          </li>
        ))}
      </Section>

      <Section title="Recent activity" empty="No activity logged yet." count={history.length}>
        {history.map((a) => (
          <li key={a.id}>
            <p className="flex items-baseline justify-between gap-3">
              <span className="min-w-0 truncate font-medium text-ink">{a.title}</span>
              <span className="tnum shrink-0 text-ink-3">{formatDay(a.at)}</span>
            </p>
            <p className="mt-0.5 text-ink-2">{a.detail}</p>
          </li>
        ))}
      </Section>
    </Card>
  );
}

function Section({ title, empty, count, children }: { title: string; empty: string; count: number; children: React.ReactNode }) {
  return (
    <section className="border-b border-line px-5 py-4 last:border-b-0">
      <h3 className="text-[12px] font-semibold tracking-wide text-ink-3 uppercase">{title}</h3>
      {count === 0 ? (
        <p className="mt-2 text-[13px] text-ink-3">{empty}</p>
      ) : (
        <ul className="mt-2.5 space-y-2.5 text-[13px]">{children}</ul>
      )}
    </section>
  );
}
