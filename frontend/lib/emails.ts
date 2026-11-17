/* Emails copied onto records by inbox sync (backend: InboxController). */

import { api, qs } from "@/lib/api";
import type { Disconnected } from "@/lib/google";

export type EmailItem = {
  id: string;
  direction: "IN" | "OUT";
  from: string;
  to: string;
  subject: string;
  snippet: string | null;
  sentAt: string;
  /** Opens the message in the signed-in user's Gmail. Emails are only ever the user's own. */
  gmailUrl: string | null;
};

export type EmailOwner = { contactId: string } | { leadId: string } | { dealId: string };

export type InboxStatus = {
  configured: boolean;
  connected: boolean;
  needsReconnect: boolean;
  email: string | null;
  backfilling: boolean;
  lastSyncedAt: string | null;
  lastError: string | null;
  imported: number;
};

export const listEmails = (owner: EmailOwner) => api<EmailItem[]>(`/emails${qs(owner)}`);

export const inboxStatus = () => api<InboxStatus>("/inbox/status");

export const turnOffInbox = (deleteImported: boolean) =>
  api<Disconnected>(`/inbox${qs({ deleteImported })}`, { method: "DELETE" });
