/* Teams (backend: TeamController). Everyone can read them; only admins change them. */

import { api } from "@/lib/api";

export type Team = { id: string; name: string; managerId: string | null; managerName: string | null; memberCount: number };

export const listTeams = () => api<Team[]>("/teams");

export const createTeam = (body: { name: string; managerId?: string }) =>
  api<Team>("/teams", { method: "POST", body: JSON.stringify(body) });

/** Every field is optional. `removeManager` takes the manager off the team. */
export const updateTeam = (id: string, body: { name?: string; managerId?: string; removeManager?: boolean }) =>
  api<Team>(`/teams/${id}`, { method: "PATCH", body: JSON.stringify(body) });

/** A team can only be deleted once it has no members. */
export const deleteTeam = (id: string) => api(`/teams/${id}`, { method: "DELETE" });
