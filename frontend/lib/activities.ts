/* The activity timeline (backend: ActivityController). */

import { api, qs } from "@/lib/api";
import type { PageResponse } from "@/lib/page";

export type ActivityType = "NOTE" | "CALL" | "MEETING" | "EMAIL_LOG" | "SYSTEM";

export type Activity = {
  id: string;
  type: ActivityType;
  subject: string;
  body: string | null;
  occurredAt: string;
  actorId: string | null;
  actorName: string | null;
  leadId: string | null;
  contactId: string | null;
  dealId: string | null;
  /** The person or deal it is about, and their company. */
  about: { name: string; company: string | null } | null;
};

export type ActivityFilters = {
  type?: ActivityType | "";
  actorId?: string;
  leadId?: string;
  contactId?: string;
  dealId?: string;
  page?: number;
  size?: number;
};

export const listActivities = (filters: ActivityFilters) => api<PageResponse<Activity>>(`/activities${qs(filters)}`);

export const activityCounts = (actorId?: string) =>
  api<Partial<Record<ActivityType, number>>>(`/activities/counts${qs({ actorId })}`);

export const logActivity = (body: {
  type: ActivityType;
  subject: string;
  body?: string;
  contactId?: string;
  leadId?: string;
  dealId?: string;
}) => api<Activity>("/activities", { method: "POST", body: JSON.stringify(body) });

export const updateActivity = (id: string, body: { subject?: string; body?: string; version?: number }) =>
  api<Activity>(`/activities/${id}`, { method: "PATCH", body: JSON.stringify(body) });

export const deleteActivity = (id: string) => api(`/activities/${id}`, { method: "DELETE" });
