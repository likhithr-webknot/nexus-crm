"use client";

import { Page, PageHeader, buttonPrimary } from "@/components/ui/primitives";
import { listContacts } from "@/lib/contacts";
import { KEYS } from "@/lib/query-keys";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useState } from "react";
import { Drawer } from "@/components/ui/drawer";
import { AddContactDialog } from "./add-contact-dialog";
import { ContactDetail } from "./contact-detail";
import { ContactList } from "./contact-list";

export function ContactsView() {
  const [adding, setAdding] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | undefined>();

  const q = query.trim();
  const list = useQuery({ queryKey: [...KEYS.contacts, q], queryFn: () => listContacts({ q, size: 100 }), placeholderData: keepPreviousData });
  const contacts = list.data?.items ?? [];
  const selected = contacts.find((c) => c.id === selectedId);
  const select = setSelectedId;

  return (
    <Page>
      <PageHeader title="Contacts" subtitle="Everyone you sell to, with their deals, follow-ups and history in one place.">
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          Add contact
        </button>
      </PageHeader>

      <ContactList query={query} onQuery={setQuery} result={list.data} status={list.status} selectedId={selected?.id} onSelect={select} onRetry={() => list.refetch()} />

      {/* The details slide in over the list, which keeps its place and its search. */}
      <Drawer open={Boolean(selected)} onClose={() => setSelectedId(undefined)} title={selected?.name ?? "Contact"} subtitle={selected?.company?.name ?? "No company"}>
        {selected && <ContactDetail key={selected.id} contact={selected} />}
      </Drawer>

      <AddContactDialog open={adding} onClose={() => setAdding(false)} onAdded={(c) => select(c.id)} />
    </Page>
  );
}
