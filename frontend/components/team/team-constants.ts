import { ApiError } from "@/lib/api";
import { ROLE_LABELS, type Role } from "@/lib/auth";
import type { UserStatus } from "@/lib/users";

export const ROLES = Object.keys(ROLE_LABELS) as Role[];
export const ROLE_TONE: Record<Role, "violet" | "brand" | "neutral"> = {
  ADMIN: "violet",
  MANAGER: "brand",
  REP: "neutral",
};
export const STATUS: Record<UserStatus, { label: string; dot: string }> = {
  ACTIVE: { label: "Active", dot: "tone-solid tone-green" },
  INVITED: { label: "Invited", dot: "tone-solid tone-amber" },
  DISABLED: { label: "Disabled", dot: "bg-line-strong" },
};

/** What to tell the user when a save fails. The backend's own sentence when it sent one. */
export const failure = (error: unknown) =>
  error instanceof ApiError && error.code !== "unknown" && error.status < 500
    ? error.message
    : "Something went wrong on our side. Try again.";
