"use client";

import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useOwnerOptions } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { FOLLOW_UP_TYPE_LABELS, updateFollowUp, type FollowUpType } from "@/lib/follow-ups";
import { toInstant } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { TYPE_STYLES, type Item } from "./follow-up-item";

export function EditFollowUpDialog({ item, onClose }: { item: Item | null; onClose: () => void }) {
  const owners = useOwnerOptions(item !== null);
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setError(null);
    onClose();
  };

  const save = useMutation({
    mutationFn: (v: Parameters<typeof updateFollowUp>) => updateFollowUp(...v),
    onSuccess: (updated) => {
      toast(`${updated.title} updated.`);
      void refresh(AFTER.followUp);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      key={item ? `${item.id}-${item.version}` : "none"}
      open={item !== null}
      onClose={close}
      title="Edit follow-up"
      description={item?.about ? `${item.about.name}${item.about.company ? `, ${item.about.company}` : ""}` : undefined}
      submitLabel="Save changes"
      pending={save.isPending}
      error={error}
      onSubmit={(f) => {
        if (!item) return;
        setError(null);
        save.mutate([item.id, { title: f.title, type: f.type as FollowUpType, dueAt: toInstant(f.date, f.time), assigneeId: f.owner || undefined, version: item.version }]);
      }}
    >
      <Field label="What needs doing" className="sm:col-span-2">
        <input name="title" required maxLength={160} defaultValue={item?.title} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Type">
        <select name="type" defaultValue={item?.type} className={fieldControl}>
          {(Object.keys(TYPE_STYLES) as FollowUpType[]).map((t) => (
            <option key={t} value={t}>
              {FOLLOW_UP_TYPE_LABELS[t]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Assigned to">
        <select name="owner" defaultValue={item?.assigneeId} className={fieldControl}>
          {owners.map((u) => (
            <option key={u.id} value={u.id}>
              {u.displayName}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Date">
        <input name="date" type="date" required defaultValue={item?.local.slice(0, 10)} className={fieldControl} />
      </Field>
      <Field label="Time">
        <input name="time" type="time" required defaultValue={item?.local.slice(11, 16)} className={fieldControl} />
      </Field>
    </FormDialog>
  );
}
