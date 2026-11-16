/* The users API (backend: UserController). Reading is open to every role; inviting and changing need an admin. */

import { api } from "@/lib/api";
import type { Role, SessionUser } from "@/lib/auth";

export type UserStatus = "INVITED" | "ACTIVE" | "DISABLED";

export type User = SessionUser & { status: UserStatus; lastLoginAt: string | null; teamId: string | null };

export const listUsers = () => api<User[]>("/users");

export const inviteUser = (body: { email: string; displayName: string; role: Role }) =>
  api<User>("/users", { method: "POST", body: JSON.stringify(body) });

/** Changes a user's role, status or both. A field that is left out stays as it is. */
export const updateUser = (id: string, body: { role?: Role; status?: "ACTIVE" | "DISABLED"; displayName?: string }) =>
  api<User>(`/users/${id}`, { method: "PATCH", body: JSON.stringify(body) });

/** The people the signed-in user can give a lead, deal or follow-up to. */
export const listAssignable = () => api<User[]>("/users/assignable");

/** Removes someone who was invited but never signed in. Anyone else is disabled instead. */
export const deleteUser = (id: string) => api(`/users/${id}`, { method: "DELETE" });

/** Puts a user in a team, or takes them out of theirs when teamId is null. */
export const setUserTeam = (id: string, teamId: string | null) =>
  api<User>(`/users/${id}/team`, { method: "PUT", body: JSON.stringify({ teamId }) });
