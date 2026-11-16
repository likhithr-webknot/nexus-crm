"use client";

import { Page, PageHeader, buttonPrimary } from "@/components/ui/primitives";
import { listContacts } from "@/lib/contacts";
import { KEYS } from "@/lib/query-keys";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useRef, useState } from "react";
import { AddContactDialog } from "./add-contact-dialog";
import { ContactDetail } from "./contact-detail";
import { ContactList } from "./contact-list";

export function ContactsView() {
  const [adding, setAdding] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | undefined>();
  const detailRef = useRef<HTMLDivElement>(null);

  const q = query.trim();
  const list = useQuery({ queryKey: [...KEYS.contacts, q], queryFn: () => listContacts({ q, size: 100 }), placeholderData: keepPreviousData });
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
        <ContactList query={query} onQuery={setQuery} result={list.data} status={list.status} selectedId={selected?.id} onSelect={select} onRetry={() => list.refetch()} />
        <div ref={detailRef} className="lg:sticky lg:top-6">
          {selected ? <ContactDetail contact={selected} /> : null}
        </div>
      </div>

      <AddContactDialog open={adding} onClose={() => setAdding(false)} onAdded={(c) => select(c.id)} />
    </Page>
  );
}
