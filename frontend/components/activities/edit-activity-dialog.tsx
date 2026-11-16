"use client";

import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { updateActivity, type Activity } from "@/lib/activities";
import { errorMessage } from "@/lib/api";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

/** Changes the summary and notes of an entry. Only its author can do this. */
export function EditActivityDialog({ activity, onClose }: { activity: Activity | null; onClose: () => void }) {
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setError(null);
    onClose();
  };

  const save = useMutation({
    mutationFn: (v: Parameters<typeof updateActivity>) => updateActivity(...v),
    onSuccess: () => {
      toast("Activity updated.");
      void refresh(AFTER.activity);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      key={activity?.id ?? "none"}
      open={activity !== null}
      onClose={close}
      title="Edit activity"
      submitLabel="Save changes"
      pending={save.isPending}
      error={error}
      onSubmit={(f) => activity && (setError(null), save.mutate([activity.id, { subject: f.title, body: f.detail }]))}
    >
      <Field label="Summary" className="sm:col-span-2">
        <input name="title" required maxLength={160} defaultValue={activity?.subject} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Notes" className="sm:col-span-2">
        <textarea name="detail" maxLength={5000} rows={4} defaultValue={activity?.body ?? ""} className={`${fieldControl} h-auto py-2`} />
      </Field>
    </FormDialog>
  );
}
