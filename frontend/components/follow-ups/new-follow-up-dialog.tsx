"use client";

import { useSession } from "@/components/auth/session";
import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { useContactOptions } from "@/hooks/use-contact-options";
import { useOwnerOptions } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { FOLLOW_UP_TYPE_LABELS, createFollowUp, type FollowUp, type FollowUpType } from "@/lib/follow-ups";
import { toInstant } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { TYPE_STYLES } from "./follow-up-item";
import { listLeads } from "@/lib/leads";
import { listDeals } from "@/lib/deals";
import { useQuery } from "@tanstack/react-query";

export function NewFollowUpDialog({ open, onClose, defaultDay, onAdded, defaultRelated }: { open: boolean; onClose: () => void; defaultDay: string; onAdded: (followUp: FollowUp) => void; defaultRelated?: { type: "lead" | "contact" | "deal"; id: string } }) {
  const session = useSession();
  const owners = useOwnerOptions(open);
  const contacts = useContactOptions(open);
  const leads = useQuery({ queryKey: ["follow-up-options", "leads"], queryFn: () => listLeads({ size: 100 }), enabled: open });
  const deals = useQuery({ queryKey: ["follow-up-options", "deals"], queryFn: () => listDeals({ size: 100 }), enabled: open });
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: createFollowUp,
    onSuccess: (followUp) => {
      void refresh(AFTER.followUp);
      onAdded(followUp);
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      open={open}
      onClose={onClose}
      title="New follow-up"
      description="A reminder to get back to someone."
      submitLabel="Schedule"
      pending={add.isPending}
      error={error}
      onSubmit={(f) => {
        setError(null);
        const [kind, id] = f.related?.split(":") ?? [];
        add.mutate({ title: f.title, type: f.type as FollowUpType, leadId: kind === "lead" ? id : undefined, contactId: kind === "contact" ? id : undefined, dealId: kind === "deal" ? id : undefined, assigneeId: f.owner || undefined, dueAt: toInstant(f.date, f.time) });
      }}
    >
      <Field label="What needs doing" className="sm:col-span-2">
        <input name="title" required maxLength={160} autoComplete="off" placeholder="Send revised proposal" className={fieldControl} />
      </Field>
      <Field label="Type">
        <select name="type" className={fieldControl}>
          {(Object.keys(TYPE_STYLES) as FollowUpType[]).map((t) => (
            <option key={t} value={t}>
              {FOLLOW_UP_TYPE_LABELS[t]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Related record" optional>
        <select name="related" defaultValue={defaultRelated ? `${defaultRelated.type}:${defaultRelated.id}` : ""} className={fieldControl}>
          <option value="">No linked record</option>
          <optgroup label="Contacts">{contacts.data?.items.map((c) => <option key={c.id} value={`contact:${c.id}`}>{c.name}{c.company ? `, ${c.company.name}` : ""}</option>)}</optgroup>
          <optgroup label="Leads">{leads.data?.items.map((lead) => <option key={lead.id} value={`lead:${lead.id}`}>{lead.name}</option>)}</optgroup>
          <optgroup label="Deals">{deals.data?.items.map((deal) => <option key={deal.id} value={`deal:${deal.id}`}>{deal.name}</option>)}</optgroup>
        </select>
      </Field>
      <Field label="Date">
        <input name="date" type="date" required defaultValue={defaultDay} className={fieldControl} />
      </Field>
      <Field label="Time">
        <input name="time" type="time" required defaultValue="09:00" className={fieldControl} />
      </Field>
      <Field label="Assigned to" className="sm:col-span-2">
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
