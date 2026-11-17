"use client";

import { ContactDetail } from "@/components/contacts/contact-detail";
import { Card, Page, PageHeader, buttonSecondary } from "@/components/ui/primitives";
import { getContact } from "@/lib/contacts";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { ResearchCard } from "@/components/ai/research-card";

export function ContactRecord({ id }: { id: string }) {
  const contact = useQuery({ queryKey: [...KEYS.contacts, "record", id], queryFn: () => getContact(id) });
  if (contact.isError)
    return (
      <Page>
        <PageHeader title="Contact unavailable" subtitle="The record could not be loaded." />
        <Card className="p-6">
          <button onClick={() => contact.refetch()} className={buttonSecondary}>
            Try again
          </button>
        </Card>
      </Page>
    );
  if (!contact.data)
    return (
      <Page>
        <div className="h-52 rounded-xl skeleton" role="status" aria-label="Loading contact" />
      </Page>
    );
  return (
    <Page>
      <div className="mb-4">
        <Link href="/contacts" className="inline-flex items-center gap-1.5 text-[12.5px] text-ink-3 hover:text-brand">
          <ArrowLeft className="size-3.5" />
          All contacts
        </Link>
      </div>
      <PageHeader title={contact.data.name} subtitle={contact.data.company?.name ?? "Independent contact"} />
      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,760px)_minmax(240px,1fr)]">
        <ContactDetail contact={contact.data} full />
        <div className="space-y-5">
          <Card className="p-5">
            <h2 className="text-[15px] font-semibold">Relationship</h2>
            <p className="mt-1 text-[13px] text-ink-2">Keep context close while working with this contact.</p>
            <dl className="mt-4 space-y-3 border-t border-line pt-4 text-[12.5px]">
              <div>
                <dt className="text-ink-3">Company</dt>
                <dd className="mt-1 font-medium">
                  {contact.data.company ? (
                    <Link href={`/companies/${contact.data.company.id}`} className="text-brand hover:underline">
                      {contact.data.company.name}
                    </Link>
                  ) : (
                    "Not linked"
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-ink-3">Owner</dt>
                <dd className="mt-1 font-medium">{contact.data.owner.name}</dd>
              </div>
              <div>
                <dt className="text-ink-3">Role</dt>
                <dd className="mt-1 font-medium">{contact.data.jobTitle ?? "Not set"}</dd>
              </div>
            </dl>
          </Card>
          <ResearchCard
            kind="contact"
            id={contact.data.id}
            has={{ email: Boolean(contact.data.email) }}
            onApplied={() => void contact.refetch()}
          />
        </div>
      </div>
    </Page>
  );
}
