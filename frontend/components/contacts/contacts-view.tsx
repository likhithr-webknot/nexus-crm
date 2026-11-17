"use client";

import { RecordBulkBar } from "@/components/tags/record-bulk-bar";
import { Page, PageHeader, buttonPrimary } from "@/components/ui/primitives";
import { useTags } from "@/hooks/use-tags";
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
  const [tag, setTag] = useState("");
  const [ticked, setTicked] = useState<ReadonlySet<string>>(new Set());
  const tags = useTags();

  const q = query.trim();
  const list = useQuery({ queryKey: [...KEYS.contacts, q, tag], queryFn: () => listContacts({ q, tag, size: 100 }), placeholderData: keepPreviousData });
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

      <ContactList
        query={query}
        onQuery={setQuery}
        tag={tag}
        onTag={setTag}
        tags={tags.data ?? []}
        ticked={ticked}
        onTick={(id, on) =>
          setTicked((prev) => {
            const next = new Set(prev);
            if (on) next.add(id);
            else next.delete(id);
            return next;
          })
        }
        bulk={ticked.size > 0 ? <RecordBulkBar kind="contacts" ids={[...ticked]} onClear={() => setTicked(new Set())} /> : null}
        result={list.data} status={list.status} selectedId={selected?.id} onSelect={select} onRetry={() => list.refetch()} />

      {/* The details slide in over the list, which keeps its place and its search. */}
      <Drawer open={Boolean(selected)} onClose={() => setSelectedId(undefined)} title={selected?.name ?? "Contact"} subtitle={selected?.company?.name ?? "No company"}>
        {selected && <ContactDetail key={selected.id} contact={selected} />}
      </Drawer>

      <AddContactDialog open={adding} onClose={() => setAdding(false)} onAdded={(c) => select(c.id)} />
    </Page>
  );
}
