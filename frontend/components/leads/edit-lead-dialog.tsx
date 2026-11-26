"use client";

import { CompanySelect, companyFromForm } from "@/components/companies/company-select";
import { useCurrency } from "@/components/currency/currency-provider";
import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { tooManyDecimals } from "@/lib/amount";
import { errorMessage } from "@/lib/api";
import { splitName } from "@/lib/format";
import { updateLead, type Lead } from "@/lib/leads";
import { moneyValue } from "@/lib/money";
import { AFTER } from "@/lib/query-keys";
import { SOURCES, SOURCE_LABELS, type LeadSource } from "@/lib/stages";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

/** Changes a lead's details. Its status and owner have their own actions. */
export function EditLeadDialog({ lead, onClose }: { lead: Lead | null; onClose: () => void }) {
  const { currencies } = useCurrency();
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setError(null);
    onClose();
  };

  const save = useMutation({
    mutationFn: (v: Parameters<typeof updateLead>) => updateLead(...v),
    onSuccess: (updated) => {
      toast(`${updated.name} updated.`);
      void refresh(AFTER.lead);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const name = lead ? `${lead.firstName} ${lead.lastName}`.trim() : "";
  return (
    <FormDialog
      key={lead?.id ?? "none"}
      open={lead !== null}
      onClose={close}
      title="Edit lead"
      submitLabel="Save changes"
      pending={save.isPending}
      error={error}
      onSubmit={(f, form) => {
        if (!lead) return;
        if (tooManyDecimals(form, "value", f.value, f.currency)) return;
        setError(null);
        save.mutate([
          lead.id,
          {
            ...splitName(f.name),
            email: f.email,
            phone: f.phone,
            ...companyFromForm(f, true),
            source: f.source as LeadSource,
            estimatedValue: f.value ? { amount: f.value, currency: f.currency } : undefined,
            version: lead.version,
          },
        ]);
      }}
    >
      <Field label="Full name">
        <input name="name" required maxLength={240} defaultValue={name} autoComplete="off" className={fieldControl} />
      </Field>
      <CompanySelect current={lead?.company} enabled={lead !== null} />
      <Field label="Email">
        <input
          name="email"
          type="email"
          required
          maxLength={254}
          defaultValue={lead?.email ?? ""}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
      <Field label="Phone" optional>
        <input
          name="phone"
          type="tel"
          maxLength={40}
          defaultValue={lead?.phone ?? ""}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
      <Field label="Source">
        <select name="source" defaultValue={lead?.source ?? "MANUAL"} className={fieldControl}>
          {SOURCES.map((s) => (
            <option key={s} value={s}>
              {SOURCE_LABELS[s]}
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
          defaultValue={lead?.estimatedValue ? moneyValue(lead.estimatedValue) : ""}
          onChange={(e) => e.currentTarget.setCustomValidity("")}
          className={fieldControl}
        />
      </Field>
      <Field label="Currency">
        <select
          name="currency"
          defaultValue={lead?.estimatedValue?.currency ?? currencies[0]?.code}
          className={fieldControl}
        >
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
