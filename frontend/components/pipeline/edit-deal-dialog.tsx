"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { FormDialog } from "@/components/ui/dialog";
import { Field, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useContactOptions } from "@/hooks/use-contact-options";
import { useOwnerOptions } from "@/hooks/use-people";
import { tooManyDecimals } from "@/lib/amount";
import { errorMessage } from "@/lib/api";
import { updateDeal, type Deal } from "@/lib/deals";
import { moneyValue } from "@/lib/money";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

/** Changes a deal's details. Moving it between stages is done on the board. */
export function EditDealDialog({ deal, onClose, onSaved, onDelete }: { deal: Deal | null; onClose: () => void; onSaved: () => void; onDelete: (deal: Deal) => void }) {
  const { currencies } = useCurrency();
  const owners = useOwnerOptions(deal !== null);
  const contacts = useContactOptions(deal !== null);
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setError(null);
    onClose();
  };

  const save = useMutation({
    mutationFn: (v: Parameters<typeof updateDeal>) => updateDeal(...v),
    onSuccess: (updated) => {
      toast(`${updated.name} updated.`);
      onSaved();
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      key={deal ? `${deal.id}-${deal.version}` : "none"}
      open={deal !== null}
      onClose={close}
      title="Edit deal"
      description={deal ? `${deal.company.name}` : undefined}
      submitLabel="Save changes"
      pending={save.isPending}
      error={error}
      onSubmit={(f, form) => {
        if (!deal) return;
        // A closed deal keeps its currency, because its INR value was saved when it closed.
        if (tooManyDecimals(form, "value", f.value, f.currency)) return;
        setError(null);
        save.mutate([
          deal.id,
          {
            name: f.name,
            primaryContactId: f.contact || undefined,
            ownerId: f.owner || undefined,
            amount: { amount: f.value, currency: f.currency },
            expectedCloseDate: f.close || undefined,
            version: deal.version,
          },
        ]);
      }}
    >
      <Field label="Deal name" className="sm:col-span-2">
        <input name="name" required maxLength={160} defaultValue={deal?.name} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Value">
        <input name="value" type="number" required min={0} step="any" inputMode="decimal" defaultValue={deal ? moneyValue(deal.amount) : ""} onChange={(e) => e.currentTarget.setCustomValidity("")} className={fieldControl} />
      </Field>
      <Field label="Currency">
        <select name="currency" defaultValue={deal?.amount.currency} disabled={Boolean(deal?.closedAt)} className={fieldControl}>
          {currencies.map(({ code, name }) => (
            <option key={code} value={code}>
              {code} — {name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Contact" optional>
        <select name="contact" defaultValue={deal?.primaryContact?.id ?? ""} className={fieldControl}>
          <option value="">No contact</option>
          {contacts.data?.items.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.company ? `, ${c.company.name}` : ""}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Owner">
        <select name="owner" defaultValue={deal?.owner.id} className={fieldControl}>
          {owners.map((u) => (
            <option key={u.id} value={u.id}>
              {u.displayName}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Expected close date" optional>
        <input name="close" type="date" defaultValue={deal?.expectedCloseDate ?? ""} className={fieldControl} />
      </Field>
      <div className="flex items-end">
        <button type="button" className={buttonSecondary + " text-down"} onClick={() => deal && (close(), onDelete(deal))}>
          Delete deal
        </button>
      </div>
    </FormDialog>
  );
}
