"use client";

import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { ROLE_LABELS, type Role } from "@/lib/auth";
import { KEYS } from "@/lib/query-keys";
import { inviteUser } from "@/lib/users";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { ROLES, failure } from "./team-constants";

export function InviteDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setError(null);
    onClose();
  };

  const invite = useMutation({
    mutationFn: inviteUser,
    onSuccess: (user) => {
      void refresh([KEYS.users]);
      toast(
        `${user.displayName} can now sign in with ${user.email}. Nexus doesn't send an email, so share the link yourself.`,
      );
      close();
    },
    onError: (e) => setError(failure(e)),
  });

  return (
    <FormDialog
      open={open}
      onClose={close}
      title="Invite member"
      description="They sign in with the Google account for this email. No email is sent, so tell them yourself."
      submitLabel="Invite"
      pending={invite.isPending}
      error={error}
      onSubmit={(f) => {
        setError(null);
        invite.mutate({ email: f.email, displayName: f.displayName, role: f.role as Role });
      }}
    >
      <Field label="Google email" className="sm:col-span-2">
        <input name="email" type="email" required maxLength={254} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Name">
        <input name="displayName" required maxLength={120} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Role" hint="Reps see their own records, managers their team's, admins everything.">
        <select name="role" defaultValue="REP" className={fieldControl}>
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {ROLE_LABELS[r]}
            </option>
          ))}
        </select>
      </Field>
    </FormDialog>
  );
}
