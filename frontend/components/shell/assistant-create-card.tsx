"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, Building2, Check, ContactRound, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { api, errorMessage } from "@/lib/api";
import { createCompany, createContact } from "@/lib/contacts";
import { fieldControl, buttonPrimary } from "@/components/ui/primitives";
import { KEYS } from "@/lib/query-keys";

type Kind = "contact" | "company";
type Draft = {
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  jobTitle: string;
  companyName: string;
  name: string;
  domain: string;
  industry: string;
};
const blank: Draft = {
  firstName: "",
  lastName: "",
  phone: "",
  email: "",
  jobTitle: "",
  companyName: "",
  name: "",
  domain: "",
  industry: "",
};

export function AssistantCreateCard({ kind, request }: { kind: Kind; request: string }) {
  const [draft, setDraft] = useState<Draft>(blank);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<string | null>(null);
  const client = useQueryClient();
  useEffect(() => {
    let mounted = true;
    api<Draft>(`/ai/record-draft/${kind}`, { method: "POST", body: JSON.stringify({ text: request }) })
      .then((value) => {
        if (mounted) setDraft(value);
      })
      .catch(() => {
        if (mounted) setError("Fill in the details below to create this record.");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [kind, request]);
  const change = (field: keyof Draft, value: string) => setDraft((current) => ({ ...current, [field]: value }));
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      if (kind === "contact") {
        const item = await createContact({
          firstName: draft.firstName.trim(),
          lastName: draft.lastName.trim(),
          phone: draft.phone.trim() || undefined,
          email: draft.email.trim() || undefined,
          jobTitle: draft.jobTitle.trim() || undefined,
          companyName: draft.companyName.trim() || undefined,
        });
        setCreated(item.id);
        await client.invalidateQueries({ queryKey: KEYS.contacts });
      } else {
        const item = await createCompany({
          name: draft.name.trim(),
          domain: draft.domain.trim() || undefined,
          industry: draft.industry.trim() || undefined,
        });
        setCreated(item.id);
        await client.invalidateQueries({ queryKey: KEYS.companies });
      }
    } catch (cause) {
      setError(errorMessage(cause, "This record could not be created."));
    } finally {
      setSaving(false);
    }
  };
  return (
    <section className="mt-3 overflow-hidden rounded-2xl border border-brand/25 bg-surface shadow-[0_14px_36px_-24px_rgba(42,65,115,.45)] motion-safe:animate-[drawer-in_320ms_var(--ease-enter)]">
      <div className="flex items-center gap-2.5 border-b border-line bg-brand-soft/70 px-4 py-3">
        <span className="grid size-8 place-items-center rounded-lg bg-surface text-brand">
          {kind === "contact" ? <ContactRound className="size-4" /> : <Building2 className="size-4" />}
        </span>
        <div className="flex-1">
          <p className="text-[13px] font-semibold">New {kind}</p>
          <p className="text-[11px] text-ink-3">Review details, then create in CRM</p>
        </div>
        {loading && <LoaderCircle className="size-4 animate-spin text-brand" />}
      </div>
      {created ? (
        <div className="space-y-2 p-4 text-[12px]">
          <p className="flex items-center gap-2 font-medium text-up">
            <Check className="size-4" />
            {kind === "contact" ? "Contact" : "Company"} created in CRM.
          </p>
          <Link
            href={`/${kind === "contact" ? "contacts" : "companies"}/${created}`}
            className="inline-flex items-center gap-1 font-medium text-brand"
          >
            Open record
            <ArrowUpRight className="size-3.5" />
          </Link>
        </div>
      ) : (
        <form onSubmit={(event) => void submit(event)} className="space-y-3 p-4">
          {kind === "contact" ? (
            <>
              <div className="grid grid-cols-2 gap-2">
                <label className="text-[11px] font-medium text-ink-2">
                  First name *
                  <input
                    required
                    maxLength={120}
                    value={draft.firstName}
                    onChange={(event) => change("firstName", event.target.value)}
                    className={fieldControl}
                  />
                </label>
                <label className="text-[11px] font-medium text-ink-2">
                  Last name *
                  <input
                    required
                    maxLength={120}
                    value={draft.lastName}
                    onChange={(event) => change("lastName", event.target.value)}
                    className={fieldControl}
                  />
                </label>
              </div>
              <label className="block text-[11px] font-medium text-ink-2">
                Phone number
                <input
                  type="tel"
                  maxLength={40}
                  value={draft.phone}
                  onChange={(event) => change("phone", event.target.value)}
                  placeholder="Ask the contact if unknown"
                  className={fieldControl}
                />
              </label>
              <label className="block text-[11px] font-medium text-ink-2">
                Email
                <input
                  type="email"
                  maxLength={254}
                  value={draft.email}
                  onChange={(event) => change("email", event.target.value)}
                  className={fieldControl}
                />
              </label>
              <label className="block text-[11px] font-medium text-ink-2">
                Company
                <input
                  maxLength={160}
                  value={draft.companyName}
                  onChange={(event) => change("companyName", event.target.value)}
                  className={fieldControl}
                />
              </label>
              <label className="block text-[11px] font-medium text-ink-2">
                Job title
                <input
                  maxLength={120}
                  value={draft.jobTitle}
                  onChange={(event) => change("jobTitle", event.target.value)}
                  className={fieldControl}
                />
              </label>
            </>
          ) : (
            <>
              <label className="block text-[11px] font-medium text-ink-2">
                Company name *
                <input
                  required
                  maxLength={160}
                  value={draft.name}
                  onChange={(event) => change("name", event.target.value)}
                  className={fieldControl}
                />
              </label>
              <label className="block text-[11px] font-medium text-ink-2">
                Domain
                <input
                  maxLength={160}
                  value={draft.domain}
                  onChange={(event) => change("domain", event.target.value)}
                  placeholder="example.com"
                  className={fieldControl}
                />
              </label>
              <label className="block text-[11px] font-medium text-ink-2">
                Industry
                <input
                  maxLength={120}
                  value={draft.industry}
                  onChange={(event) => change("industry", event.target.value)}
                  className={fieldControl}
                />
              </label>
            </>
          )}
          {error && (
            <p role="alert" className="text-[11px] text-down">
              {error}
            </p>
          )}
          <button disabled={loading || saving} className={buttonPrimary + " w-full justify-center"}>
            {saving ? "Creating…" : `Create ${kind}`}
          </button>
        </form>
      )}
    </section>
  );
}
