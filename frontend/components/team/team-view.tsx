"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, SearchX, UserPlus, WifiOff } from "lucide-react";
import { ApiError } from "@/lib/api";
import { ROLE_LABELS, type Role } from "@/lib/auth";
import { deleteUser, inviteUser, listUsers, setUserTeam, updateUser, type User, type UserStatus } from "@/lib/users";
import { listTeams } from "@/lib/teams";
import { TeamsCard } from "./teams-card";
import { UserAvatar, useSession } from "@/components/auth/session";
import { ConfirmDialog, Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import {
  Badge,
  Card,
  EmptyState,
  Field,
  Page,
  PageHeader,
  buttonPrimary,
  buttonSecondary,
  control,
  fieldControl,
} from "@/components/ui/primitives";
import { cn, formatDate, readForm } from "@/lib/format";

const ROLES = Object.keys(ROLE_LABELS) as Role[];
const ROLE_TONE: Record<Role, "violet" | "brand" | "neutral"> = { ADMIN: "violet", MANAGER: "brand", REP: "neutral" };
const STATUS: Record<UserStatus, { label: string; dot: string }> = {
  ACTIVE: { label: "Active", dot: "bg-emerald-500" },
  INVITED: { label: "Invited", dot: "bg-amber-500" },
  DISABLED: { label: "Disabled", dot: "bg-slate-300" },
};

/** What to tell the user when a save fails. The backend's own sentence when it sent one. */
const failure = (error: unknown) =>
  error instanceof ApiError && error.code !== "unknown" && error.status < 500
    ? error.message
    : "Something went wrong on our side. Try again.";

export function TeamView() {
  const me = useSession();
  const isAdmin = me.role === "ADMIN";
  const { data: users, error, refetch, isFetching } = useQuery({ queryKey: ["users"], queryFn: listUsers });
  const [query, setQuery] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [inviting, setInviting] = useState(false);
  const [managing, setManaging] = useState<User | null>(null);

  const q = query.trim().toLowerCase();
  const shown = (users ?? []).filter(
    (u) => (!role || u.role === role) && (!q || u.displayName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)),
  );
  const count = (status: UserStatus) => (users ?? []).filter((u) => u.status === status).length;

  return (
    <Page>
      <PageHeader
        title="Team Management"
        subtitle={
          users
            ? `${count("ACTIVE")} active, ${count("INVITED")} invited, ${count("DISABLED")} disabled. ${
                isAdmin ? "Access is invite-only." : "Only admins can invite or change members."
              }`
            : "Who has access to Nexus, and what they can do."
        }
      >
        {isAdmin && (
          <button className={buttonPrimary} onClick={() => setInviting(true)}>
            <UserPlus className="size-4" aria-hidden />
            Invite member
          </button>
        )}
      </PageHeader>

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 p-4">
          <label className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <span className="sr-only">Search team</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search name or email…"
              className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
            />
          </label>
          <select
            aria-label="Role"
            value={role}
            onChange={(e) => setRole(e.target.value as Role | "")}
            className={cn(control, "px-2.5 sm:ml-auto")}
          >
            <option value="">All roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABELS[r]}
              </option>
            ))}
          </select>
        </div>

        <div className="border-t border-line">
          {!users && error ? (
            <EmptyState icon={WifiOff} title="Couldn't load the team" body="The server didn't respond. Check your connection, then try again.">
              <button onClick={() => refetch()} disabled={isFetching} className={buttonSecondary}>
                Try again
              </button>
            </EmptyState>
          ) : !users ? (
            <ul aria-label="Loading team" aria-busy className="divide-y divide-line">
              {[0, 1, 2].map((i) => (
                <li key={i} className="flex animate-pulse items-center gap-3 px-5 py-3.5 motion-reduce:animate-none">
                  <span className="size-8 rounded-full bg-canvas" />
                  <span className="h-3 w-40 rounded bg-canvas" />
                  <span className="ml-auto h-3 w-24 rounded bg-canvas" />
                </li>
              ))}
            </ul>
          ) : shown.length === 0 ? (
            <EmptyState icon={SearchX} title="No team members found" body="Nobody matches this search and role.">
              <button
                onClick={() => {
                  setQuery("");
                  setRole("");
                }}
                className={buttonSecondary}
              >
                Clear filters
              </button>
            </EmptyState>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-[13px]">
                <thead>
                  <tr className="border-b border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
                    <th className="py-2.5 pr-3 pl-5 font-medium">Member</th>
                    <th className="px-3 py-2.5 font-medium">Role</th>
                    <th className="px-3 py-2.5 font-medium">Status</th>
                    <th className="px-3 py-2.5 font-medium">Last sign-in</th>
                    {isAdmin && <th className="py-2.5 pr-5 pl-3 text-right font-medium">Actions</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {shown.map((u) => (
                    <tr key={u.id} className="hover:bg-canvas/60">
                      <td className="py-3 pr-3 pl-5">
                        <div className="flex items-center gap-3">
                          <UserAvatar user={u} />
                          <div className="min-w-0">
                            <p className={cn("flex items-center gap-2 font-medium", u.status === "DISABLED" ? "text-ink-3" : "text-ink")}>
                              {u.displayName}
                              {u.id === me.id && <Badge>You</Badge>}
                            </p>
                            <p className="truncate text-[12.5px] text-ink-3">{u.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        <Badge tone={ROLE_TONE[u.role]}>{ROLE_LABELS[u.role]}</Badge>
                      </td>
                      <td className="px-3 py-3">
                        <span className="inline-flex items-center gap-2 text-ink-2">
                          <span className={cn("size-2 rounded-full", STATUS[u.status].dot)} aria-hidden />
                          {STATUS[u.status].label}
                        </span>
                      </td>
                      <td className="tnum px-3 py-3 whitespace-nowrap text-ink-2">
                        {u.lastLoginAt ? formatDate(u.lastLoginAt) : "Never signed in"}
                      </td>
                      {isAdmin && (
                        <td className="py-3 pr-5 pl-3 text-right">
                          {/* The backend refuses changes to your own account, so there is always an admin left. */}
                          {u.id !== me.id && (
                            <button onClick={() => setManaging(u)} className={cn(buttonSecondary, "h-8")}>
                              Manage
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>

      <TeamsCard isAdmin={isAdmin} />

      <InviteDialog open={inviting} onClose={() => setInviting(false)} />
      <ManageDialog user={managing} onClose={() => setManaging(null)} />
    </Page>
  );
}

function InviteDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const invite = useMutation({
    mutationFn: inviteUser,
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast(`${user.displayName} can now sign in with ${user.email}. Nexus doesn't send an email, so share the link yourself.`);
      close();
    },
  });
  const close = () => {
    invite.reset();
    onClose();
  };

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    invite.mutate({ email: f.email, displayName: f.displayName, role: f.role as Role });
  };

  return (
    <Dialog
      open={open}
      onClose={close}
      title="Invite member"
      description="They sign in with the Google account for this email. No email is sent, so tell them yourself."
    >
      <form onSubmit={submit}>
        <DialogBody>
          <Field label="Google email">
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
          {invite.error && <FormError>{failure(invite.error)}</FormError>}
        </DialogBody>
        <DialogActions onCancel={close} submitLabel="Invite" pending={invite.isPending} />
      </form>
    </Dialog>
  );
}

function ManageDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  return (
    <Dialog open={user !== null} onClose={onClose} title={user ? `Manage ${user.displayName}` : ""} description={user?.email}>
      {user && <ManageForm user={user} onClose={onClose} />}
    </Dialog>
  );
}

function ManageForm({ user, onClose }: { user: User; onClose: () => void }) {
  const queryClient = useQueryClient();
  const teams = useQuery({ queryKey: ["teams"], queryFn: listTeams });
  const [name, setName] = useState(user.displayName);
  const [role, setRole] = useState(user.role);
  const [teamId, setTeamId] = useState(user.teamId ?? "");
  const [disabled, setDisabled] = useState(user.status === "DISABLED");
  const [removing, setRemoving] = useState(false);
  const wasDisabled = user.status === "DISABLED";
  // Someone who never signed in (a mistyped invite, for example) can be removed. Everyone else is disabled instead.
  const removable = user.status === "INVITED" && user.lastLoginAt === null;
  const changed =
    name.trim() !== user.displayName || role !== user.role || disabled !== wasDisabled || teamId !== (user.teamId ?? "");

  const refresh = () => {
    for (const key of ["users", "teams", "assignable"]) void queryClient.invalidateQueries({ queryKey: [key] });
  };

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
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} className={fieldControl} />
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
            <input type="checkbox" checked={disabled} onChange={(e) => setDisabled(e.target.checked)} className="mt-0.5 size-4 accent-rose-600" />
            <span>
              <span className="block text-[13px] font-medium text-ink">Disable this account</span>
              <span className="block text-[12.5px] text-ink-3">
                They are signed out everywhere straight away and can&apos;t sign in again until you turn this off.
              </span>
            </span>
          </label>
          {removable && (
            <button type="button" onClick={() => setRemoving(true)} className="justify-self-start text-[13px] font-medium text-down hover:underline">
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
