/* The users API (backend: UserController). Reading is open to every role; inviting and changing need an admin. */

import { api } from "@/lib/api";
import type { Role, SessionUser } from "@/lib/auth";

export type UserStatus = "INVITED" | "ACTIVE" | "DISABLED";

export type User = SessionUser & { status: UserStatus; lastLoginAt: string | null };

export const listUsers = () => api<User[]>("/users");

export const inviteUser = (body: { email: string; displayName: string; role: Role }) =>
  api<User>("/users", { method: "POST", body: JSON.stringify(body) });

/** Changes a user's role, status or both. A field that is left out stays as it is. */
export const updateUser = (id: string, body: { role?: Role; status?: "ACTIVE" | "DISABLED" }) =>
  api<User>(`/users/${id}`, { method: "PATCH", body: JSON.stringify(body) });
