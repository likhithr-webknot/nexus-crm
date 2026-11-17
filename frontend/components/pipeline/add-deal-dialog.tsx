"use client";

import { useSession } from "@/components/auth/session";
import { useCurrency } from "@/components/currency/currency-provider";
import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useContactOptions } from "@/hooks/use-contact-options";
import { useOwnerOptions } from "@/hooks/use-people";
import { tooManyDecimals } from "@/lib/amount";
import { errorMessage } from "@/lib/api";
import { createCompany, listCompanies } from "@/lib/contacts";
import { createDeal } from "@/lib/deals";
import { KEYS } from "@/lib/query-keys";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useStages } from "./use-deal-board";

export function AddDealDialog({ open, onClose, onAdded }: { open: boolean; onClose: () => void; onAdded: () => void }) {
  const { reportingCurrency, currencies } = useCurrency();
  const session = useSession();
  const owners = useOwnerOptions(open);
  const contacts = useContactOptions(open);
  const stages = useStages();
  const companies = useQuery({
    queryKey: [...KEYS.companies, "all"],
    queryFn: () => listCompanies({ size: 100 }),
    enabled: open,
  });
  const [error, setError] = useState<string | null>(null);
  const madeCompany = useRef<{ name: string; id: string } | null>(null);

  const add = useMutation({
    mutationFn: async (f: Record<string, string>) => {
      // The deal belongs to a company: use the one with this name, or create it. A company made by an attempt that then
      // failed is reused on the retry, so the retry is the same request and the server can recognise it.
      const existing = companies.data?.items.find((c) => c.name.toLowerCase() === f.company.toLowerCase());
      const justMade = madeCompany.current?.name === f.company ? madeCompany.current.id : undefined;
      const companyId = existing?.id ?? justMade ?? (await createCompany({ name: f.company })).id;
      madeCompany.current = { name: f.company, id: companyId };
      return createDeal({
        name: f.name || f.company,
        companyId,
        primaryContactId: f.contact || undefined,
        ownerId: f.owner || undefined,
        stageCode: f.stage,
        amount: { amount: f.value, currency: f.currency },
      });
    },
    onSuccess: (deal) => {
      madeCompany.current = null;
      toast(`${deal.company.name} added to the pipeline.`);
      onAdded();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (f: Record<string, string>, form: HTMLFormElement) => {
    if (tooManyDecimals(form, "value", f.value, f.currency)) return;
    setError(null);
    add.mutate(f);
  };

  return (
    <FormDialog
      open={open}
      onClose={onClose}
      title="Add deal"
      description="A deal is one sale you are working on with a company."
      submitLabel="Add deal"
      pending={add.isPending}
      error={error}
      onSubmit={submit}
    >
      <Field label="Company">
        <input
          name="company"
          list="deal-companies"
          required
          maxLength={160}
          autoComplete="off"
          className={fieldControl}
        />
        <datalist id="deal-companies">
          {companies.data?.items.map((c) => (
            <option key={c.id} value={c.name} />
          ))}
        </datalist>
      </Field>
      <Field label="Deal name" optional>
        <input
          name="name"
          maxLength={160}
          autoComplete="off"
          placeholder="Defaults to the company name"
          className={fieldControl}
        />
      </Field>
      <Field label="Contact" optional>
        <select name="contact" defaultValue="" className={fieldControl}>
          <option value="">No contact</option>
          {contacts.data?.items.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.company ? `, ${c.company.name}` : ""}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Value">
        <input
          name="value"
          type="number"
          required
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
      <Field label="Stage">
        <select name="stage" defaultValue="QUALIFIED" className={fieldControl}>
          {stages.data
            ?.filter((s) => s.kind === "OPEN")
            .map((s) => (
              <option key={s.code} value={s.code}>
                {s.name}
              </option>
            ))}
        </select>
      </Field>
      <Field label="Owner">
        <select name="owner" defaultValue={session.id} className={fieldControl}>
          {owners.map((u) => (
            <option key={u.id} value={u.id}>
              {u.displayName}
            </option>
          ))}
        </select>
      </Field>
    </FormDialog>
  );
}
