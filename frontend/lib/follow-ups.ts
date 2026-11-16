/* Follow-up tasks (backend: FollowUpController). Times are instants; screens show them in local time. */

import { api, qs } from "@/lib/api";
import type { PageResponse } from "@/lib/page";

export type FollowUpType = "CALL" | "MEETING" | "EMAIL" | "TASK";

export type FollowUp = {
  id: string;
  title: string;
  type: FollowUpType;
  notes: string | null;
  dueAt: string;
  status: "OPEN" | "DONE" | "CANCELLED";
  completedAt: string | null;
  assigneeId: string;
  assigneeName: string | null;
  leadId: string | null;
  contactId: string | null;
  dealId: string | null;
  about: { name: string; company: string | null } | null;
  version: number;
};

export type FollowUpSummary = { today: number; todayRemaining: number; upcoming: number; overdue: number; completedThisMonth: number };

export const FOLLOW_UP_TYPE_LABELS: Record<FollowUpType, string> = { CALL: "Call", MEETING: "Meeting", EMAIL: "Email", TASK: "Task" };

export const listFollowUps = (params: {
  from?: string;
  to?: string;
  status?: "OPEN" | "DONE" | "CANCELLED";
  assignee?: string;
  contactId?: string;
  leadId?: string;
  dealId?: string;
  size?: number;
}) => api<PageResponse<FollowUp>>(`/follow-ups${qs(params)}`);

export const followUpSummary = (dayStart: string, dayEnd: string, monthStart: string) =>
  api<FollowUpSummary>(`/follow-ups/summary${qs({ dayStart, dayEnd, monthStart })}`);

export const createFollowUp = (body: {
  title: string;
  type: FollowUpType;
  dueAt: string;
  assigneeId?: string;
  contactId?: string;
  leadId?: string;
  dealId?: string;
}) => api<FollowUp>("/follow-ups", { method: "POST", body: JSON.stringify(body) });

export const setFollowUpDone = (id: string, done: boolean) =>
  api<FollowUp>(`/follow-ups/${id}/${done ? "complete" : "reopen"}`, { method: "POST" });

export const updateFollowUp = (
  id: string,
  body: { title?: string; type?: FollowUpType; notes?: string; dueAt?: string; assigneeId?: string; version?: number },
) => api<FollowUp>(`/follow-ups/${id}`, { method: "PATCH", body: JSON.stringify(body) });

export const deleteFollowUp = (id: string) => api(`/follow-ups/${id}`, { method: "DELETE" });
