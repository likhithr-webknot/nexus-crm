"use client";

import { CompanySelect, companyFromForm } from "@/components/companies/company-select";
import { useSession } from "@/components/auth/session";
import { useCurrency } from "@/components/currency/currency-provider";
import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useOwnerOptions } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import { tooManyDecimals } from "@/lib/amount";
import { errorMessage } from "@/lib/api";
import { splitName } from "@/lib/format";
import { createLead } from "@/lib/leads";
import { AFTER } from "@/lib/query-keys";
import { SOURCES, SOURCE_LABELS, type LeadSource } from "@/lib/stages";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

export function AddLeadDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { reportingCurrency, currencies } = useCurrency();
  const session = useSession();
  const owners = useOwnerOptions(open);
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setError(null);
    onClose();
  };
  const add = useMutation({
    mutationFn: createLead,
    onSuccess: (lead) => {
      toast(`${lead.name} added.`);
      void refresh(AFTER.lead);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (f: Record<string, string>, form: HTMLFormElement) => {
    const currency = f.currency || reportingCurrency;
    if (tooManyDecimals(form, "value", f.value, currency)) return;
    setError(null);
    add.mutate({
      ...splitName(f.name),
      email: f.email,
      phone: f.phone || undefined,
      ...companyFromForm(f),
      source: f.source as LeadSource,
      unassigned: f.owner === "unassigned",
      ownerId: f.owner && f.owner !== "unassigned" ? f.owner : undefined,
      estimatedValue: f.value ? { amount: f.value, currency } : undefined,
    });
  };

  return (
    <FormDialog
      open={open}
      onClose={close}
      title="Add lead"
      description="New leads start in the New stage."
      submitLabel="Add lead"
      pending={add.isPending}
      error={error}
      onSubmit={submit}
    >
      <Field label="Full name">
        <input name="name" required maxLength={240} autoComplete="off" className={fieldControl} />
      </Field>
      <CompanySelect enabled={open} />
      <Field label="Email">
        <input name="email" type="email" required maxLength={254} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Phone" optional>
        <input name="phone" type="tel" maxLength={40} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Source">
        <select name="source" defaultValue="MANUAL" className={fieldControl}>
          {SOURCES.filter((s) => s !== "IMPORT").map((s) => (
            <option key={s} value={s}>
              {SOURCE_LABELS[s]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Assigned to">
        <select name="owner" defaultValue={session.id} className={fieldControl}>
          <option value="unassigned">Unassigned</option>
          {owners.map((u) => (
            <option key={u.id} value={u.id}>
              {u.displayName}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Estimated value" optional>
        <input
          name="value"
          type="number"
          min={0}
          step="any"
          inputMode="decimal"
          onChange={(e) => e.currentTarget.setCustomValidity("")}
          className={fieldControl}
        />
      </Field>
      <Field label="Currency">
        <select name="currency" defaultValue={reportingCurrency} className={fieldControl}>
          {currencies.map(({ code, name }) => (
            <option key={code} value={code}>
              {code} — {name}
            </option>
          ))}
        </select>
      </Field>
    </FormDialog>
  );
}
