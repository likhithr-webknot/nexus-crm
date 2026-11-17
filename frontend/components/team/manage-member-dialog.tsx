"use client";

import { ConfirmDialog, Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { ROLE_LABELS, type Role } from "@/lib/auth";
import { AFTER, KEYS } from "@/lib/query-keys";
import { listTeams } from "@/lib/teams";
import { deleteUser, setUserTeam, updateUser, type User } from "@/lib/users";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { ROLES, failure } from "./team-constants";

export function ManageDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  return (
    <Dialog
      open={user !== null}
      onClose={onClose}
      title={user ? `Manage ${user.displayName}` : ""}
      description={user?.email}
    >
      {user && <ManageForm user={user} onClose={onClose} />}
    </Dialog>
  );
}

function ManageForm({ user, onClose }: { user: User; onClose: () => void }) {
  const refreshKeys = useRefresh();
  const teams = useQuery({ queryKey: KEYS.teams, queryFn: listTeams });
  const [name, setName] = useState(user.displayName);
  const [role, setRole] = useState(user.role);
  const [teamId, setTeamId] = useState(user.teamId ?? "");
  const [disabled, setDisabled] = useState(user.status === "DISABLED");
  const [removing, setRemoving] = useState(false);
  const wasDisabled = user.status === "DISABLED";
  // Someone who never signed in (a mistyped invite, for example) can be removed. Everyone else is disabled instead.
  const removable = user.status === "INVITED" && user.lastLoginAt === null;
  const changed =
    name.trim() !== user.displayName ||
    role !== user.role ||
    disabled !== wasDisabled ||
    teamId !== (user.teamId ?? "");

  const refresh = () => void refreshKeys(AFTER.team);

  const save = useMutation({
    // Only what changed is sent; a field left out stays as it is.
    mutationFn: async () => {
      const fields = {
        displayName: name.trim() !== user.displayName ? name.trim() : undefined,
        role: role !== user.role ? role : undefined,
        status: disabled !== wasDisabled ? (disabled ? ("DISABLED" as const) : ("ACTIVE" as const)) : undefined,
      };
      let updated = user;
      if (fields.displayName || fields.role || fields.status) updated = await updateUser(user.id, fields);
      if (teamId !== (user.teamId ?? "")) updated = await setUserTeam(user.id, teamId || null);
      return updated;
    },
    onSuccess: (updated) => {
      refresh();
      toast(`${updated.displayName} updated.`);
      onClose();
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteUser(user.id),
    onSuccess: () => {
      refresh();
      toast(`${user.displayName} removed.`);
      onClose();
    },
  });

  return (
    <>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <DialogBody>
          <Field label="Name">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={120}
              className={fieldControl}
            />
          </Field>
          <Field label="Role">
            <select value={role} onChange={(e) => setRole(e.target.value as Role)} className={fieldControl}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABELS[r]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Team" optional>
            <select value={teamId} onChange={(e) => setTeamId(e.target.value)} className={fieldControl}>
              <option value="">No team</option>
              {(teams.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </Field>
          <label className="flex items-start gap-3 rounded-lg border border-line px-3.5 py-3">
            <input
              type="checkbox"
              checked={disabled}
              onChange={(e) => setDisabled(e.target.checked)}
              className="mt-0.5 size-4 accent-rose-600"
            />
            <span>
              <span className="block text-[13px] font-medium text-ink">Disable this account</span>
              <span className="block text-[12.5px] text-ink-3">
                They are signed out everywhere straight away and can&apos;t sign in again until you turn this off.
              </span>
            </span>
          </label>
          {removable && (
            <button
              type="button"
              onClick={() => setRemoving(true)}
              className="justify-self-start text-[13px] font-medium text-down hover:underline"
            >
              Remove this invitation…
            </button>
          )}
          {save.error && <FormError>{failure(save.error)}</FormError>}
        </DialogBody>
        <DialogActions
          onCancel={onClose}
          submitLabel={disabled && !wasDisabled ? "Disable account" : "Save changes"}
          danger={disabled && !wasDisabled}
          disabled={!changed || !name.trim()}
          pending={save.isPending}
        />
      </form>
      <ConfirmDialog
        open={removing}
        onClose={() => setRemoving(false)}
        onConfirm={() => remove.mutate()}
        pending={remove.isPending}
        error={remove.isError ? failure(remove.error) : null}
        title={`Remove ${user.displayName}?`}
        body={`${user.email} hasn't signed in yet. Removing the invitation means they can't sign in until you invite them again.`}
        confirmLabel="Remove"
      />
    </>
  );
}
