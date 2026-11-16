"use client";

import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useContactOptions } from "@/hooks/use-contact-options";
import { useRefresh } from "@/hooks/use-refresh";
import { logActivity, type ActivityType } from "@/lib/activities";
import { errorMessage } from "@/lib/api";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { TYPES } from "./activity-types";

// System entries are written by the app, never by hand.
const MANUAL_TYPES = (Object.keys(TYPES) as ActivityType[]).filter((t) => t !== "SYSTEM");

export function LogActivityDialog({ open, onClose, onLogged, defaultType = "CALL" }: { open: boolean; onClose: () => void; onLogged: () => void; defaultType?: ActivityType }) {
  const contacts = useContactOptions(open);
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);

  const log = useMutation({
    mutationFn: logActivity,
    onSuccess: (activity) => {
      toast(`${TYPES[activity.type].label} logged.`);
      void refresh(AFTER.activity);
      onLogged();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      open={open}
      onClose={onClose}
      title="Log activity"
      description="Record something that happened with a contact."
      submitLabel="Log activity"
      pending={log.isPending}
      error={error}
      onSubmit={(f) => {
        setError(null);
        log.mutate({ type: f.type as ActivityType, subject: f.title, body: f.detail, contactId: f.contact });
      }}
    >
      <Field label="Type">
        <select name="type" defaultValue={defaultType} className={fieldControl}>
          {MANUAL_TYPES.map((t) => (
            <option key={t} value={t}>
              {TYPES[t].label}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Contact">
        <select name="contact" required defaultValue="" className={fieldControl}>
          <option value="" disabled>
            Choose a contact
          </option>
          {contacts.data?.items.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.company ? `, ${c.company.name}` : ""}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Summary" className="sm:col-span-2">
        <input name="title" required maxLength={160} autoComplete="off" placeholder="Pricing call" className={fieldControl} />
      </Field>
      <Field label="Notes" className="sm:col-span-2">
        <textarea name="detail" required maxLength={5000} rows={4} className={`${fieldControl} h-auto py-2`} />
      </Field>
    </FormDialog>
  );
}
