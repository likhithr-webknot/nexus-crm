"use client";

import { ConfirmDialog, Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { Card, CardHeader, EmptyState, Field, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { AFTER, KEYS } from "@/lib/query-keys";
import { createTeam, deleteTeam, listTeams, updateTeam, type Team } from "@/lib/teams";
import { listUsers } from "@/lib/users";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Pencil, Plus, Trash2, Users } from "lucide-react";
import { useState } from "react";

/** The teams, with how many people are in each. Admins can add, rename, change the manager of, and delete them. */
export function TeamsCard({ isAdmin }: { isAdmin: boolean }) {
  const refreshKeys = useRefresh();
  const teams = useQuery({ queryKey: KEYS.teams, queryFn: listTeams });
  const [editing, setEditing] = useState<Team | "new" | null>(null);
  const [deleting, setDeleting] = useState<Team | null>(null);

  const refresh = () => void refreshKeys(AFTER.team);
  const remove = useMutation({
    mutationFn: (team: Team) => deleteTeam(team.id),
    onSuccess: (_, team) => {
      toast(`${team.name} deleted.`);
      setDeleting(null);
      refresh();
    },
  });

  return (
    <Card>
      <CardHeader
        title="Teams"
        subtitle="A manager sees the records of everyone in their team."
        action={
          isAdmin ? (
            <button className={buttonSecondary} onClick={() => setEditing("new")}>
              <Plus className="size-4" aria-hidden />
              New team
            </button>
          ) : undefined
        }
      />
      {teams.isPending ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Loading…</p>
      ) : teams.isError ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-down">Couldn&apos;t load teams.</p>
      ) : teams.data.length === 0 ? (
        <div className="border-t border-line">
          <EmptyState icon={Users} title="No teams yet" body={isAdmin ? "Create a team, then put members in it from Manage." : "An admin can create teams."} />
        </div>
      ) : (
        <ul className="divide-y divide-line border-t border-line">
          {teams.data.map((team) => (
            <li key={team.id} className="flex items-center gap-3 px-5 py-3 text-[13px]">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-ink">{team.name}</p>
                <p className="truncate text-[12.5px] text-ink-3">
                  {team.managerName ? `Led by ${team.managerName}` : "No manager"} · {team.memberCount} {team.memberCount === 1 ? "member" : "members"}
                </p>
              </div>
              {isAdmin && (
                <>
                  <button aria-label={`Edit ${team.name}`} onClick={() => setEditing(team)} className="grid size-8 place-items-center rounded-md text-ink-3 hover:bg-canvas hover:text-ink">
                    <Pencil className="size-4" aria-hidden />
                  </button>
                  <button aria-label={`Delete ${team.name}`} onClick={() => setDeleting(team)} className="grid size-8 place-items-center rounded-md text-ink-3 hover:bg-danger-soft hover:text-down">
                    <Trash2 className="size-4" aria-hidden />
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      <TeamDialog editing={editing} onClose={() => setEditing(null)} onSaved={refresh} />
      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting)}
        pending={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        title={deleting ? `Delete ${deleting.name}?` : ""}
        body="Only an empty team can be deleted. Move its members to another team first."
        confirmLabel="Delete team"
      />
    </Card>
  );
}

function TeamDialog({ editing, onClose, onSaved }: { editing: Team | "new" | null; onClose: () => void; onSaved: () => void }) {
  const team = editing === "new" ? null : editing;
  const [error, setError] = useState<string | null>(null);
  const users = useQuery({ queryKey: KEYS.users, queryFn: listUsers, enabled: editing !== null });
  // A team can only be led by a manager or an admin.
  const leaders = (users.data ?? []).filter((u) => u.role !== "REP" && u.status !== "DISABLED");

  const save = useMutation({
    mutationFn: (f: { name: string; managerId: string }) =>
      team
        ? updateTeam(team.id, { name: f.name, managerId: f.managerId || undefined, removeManager: !f.managerId && team.managerId !== null })
        : createTeam({ name: f.name, managerId: f.managerId || undefined }),
    onSuccess: (saved) => {
      toast(team ? `${saved.name} updated.` : `${saved.name} created.`);
      onSaved();
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const close = () => {
    setError(null);
    onClose();
  };

  return (
    <Dialog open={editing !== null} onClose={close} title={team ? `Edit ${team.name}` : "New team"} size="sm">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          setError(null);
          save.mutate({ name: String(data.get("name")).trim(), managerId: String(data.get("manager") ?? "") });
        }}
      >
        <DialogBody>
          <Field label="Team name">
            <input name="name" required maxLength={80} defaultValue={team?.name ?? ""} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Manager" optional>
            <select name="manager" defaultValue={team?.managerId ?? ""} className={fieldControl}>
              <option value="">No manager</option>
              {leaders.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
          {error && <FormError>{error}</FormError>}
        </DialogBody>
        <DialogActions onCancel={close} submitLabel={team ? "Save changes" : "Create team"} pending={save.isPending} />
      </form>
    </Dialog>
  );
}
