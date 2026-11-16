"use client";

import { useSession } from "@/components/auth/session";
import { Dialog, DialogBody, FormError } from "@/components/ui/dialog";
import { Field, StageBadge, buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useAssignableUsers } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { assignLead, changeLeadStatus, convertLead, type Lead } from "@/lib/leads";
import { AFTER } from "@/lib/query-keys";
import type { LeadStatus } from "@/lib/stages";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

const MOVABLE: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED"];

/** Everything you can do with one lead: change its status, give it to someone, or turn it into a deal. */
export function LeadActionsDialog({ lead, onClose, onEdit, onDelete }: { lead: Lead | null; onClose: () => void; onEdit: (lead: Lead) => void; onDelete: (lead: Lead) => void }) {
  const session = useSession();
  const refresh = useRefresh();
  const people = useAssignableUsers(lead !== null);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [disqualifying, setDisqualifying] = useState(false);
  const close = () => {
    setError(null);
    setReason("");
    setDisqualifying(false);
    onClose();
  };

  const run = useMutation({
    mutationFn: (action: () => Promise<unknown>) => action(),
    onSuccess: () => {
      void refresh(AFTER.lead);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  if (!lead) return null;
  const open = lead.status === "NEW" || lead.status === "CONTACTED" || lead.status === "QUALIFIED";
  const canAssign = session.role !== "REP";

  return (
    <Dialog open onClose={close} title={lead.name} description={lead.companyName ?? undefined}>
      <DialogBody>
        <div className="flex items-center gap-2 text-[13px] text-ink-2">
          Status: <StageBadge stage={lead.status} />
          {lead.disqualifyReason && <span className="text-ink-3">· {lead.disqualifyReason}</span>}
        </div>

        {open && (
          <Field label="Move to">
            <div className="mt-1.5 flex flex-wrap gap-2">
              {MOVABLE.filter((s) => s !== lead.status).map((s) => (
                <button key={s} type="button" className={buttonSecondary} disabled={run.isPending} onClick={() => run.mutate(() => changeLeadStatus(lead.id, s))}>
                  <StageBadge stage={s} />
                </button>
              ))}
              <button type="button" className={buttonSecondary} onClick={() => setDisqualifying(true)}>
                Disqualify…
              </button>
            </div>
          </Field>
        )}
        {disqualifying && (
          <Field label="Why is this lead not a fit?">
            <div className="mt-1.5 flex gap-2">
              <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} className={fieldControl + " mt-0"} />
              <button
                type="button"
                className={buttonPrimary}
                disabled={!reason.trim() || run.isPending}
                onClick={() => run.mutate(() => changeLeadStatus(lead.id, "DISQUALIFIED", reason.trim()))}
              >
                Disqualify
              </button>
            </div>
          </Field>
        )}

        {canAssign && open && (
          <Field label="Assigned to">
            <select
              value={lead.owner?.id ?? ""}
              disabled={run.isPending}
              onChange={(e) => run.mutate(() => assignLead(lead.id, e.target.value || null))}
              className={fieldControl}
            >
              <option value="">Unassigned</option>
              {(people.data ?? []).map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
        )}

        {open && (
          <div className="rounded-lg bg-canvas p-3.5 text-[13px] text-ink-2 ring-1 ring-line ring-inset">
            <p className="font-medium text-ink">Convert to a deal</p>
            <p className="mt-0.5">Creates the company, the contact and a deal in the pipeline from this lead.</p>
            <button
              type="button"
              className={buttonPrimary + " mt-3"}
              disabled={run.isPending}
              onClick={() =>
                run.mutate(async () => {
                  await convertLead(lead.id);
                  toast(`${lead.name} is now a deal.`);
                })
              }
            >
              Convert to deal
            </button>
          </div>
        )}
        {lead.status === "CONVERTED" && <p className="text-[13px] text-ink-3">This lead has already become a deal.</p>}
        {error && <FormError>{error}</FormError>}

        <div className="flex flex-wrap gap-2 border-t border-line pt-4">
          {lead.status !== "CONVERTED" && (
            <button type="button" className={buttonSecondary} onClick={() => { close(); onEdit(lead); }}>
              Edit details
            </button>
          )}
          <button type="button" className={buttonSecondary + " text-down"} onClick={() => { close(); onDelete(lead); }}>
            Delete lead
          </button>
        </div>
      </DialogBody>
    </Dialog>
  );
}
