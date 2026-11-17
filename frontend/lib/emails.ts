/* Emails copied onto records by inbox sync (backend: InboxController). */

import { api, qs } from "@/lib/api";

export type EmailItem = {
  id: string;
  direction: "IN" | "OUT";
  from: string;
  to: string;
  subject: string;
  snippet: string | null;
  sentAt: string;
  /** Whose mailbox the message was read from. */
  mailbox: string;
  /** Only set for the person whose mailbox it is. */
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

export const turnOffInbox = (deleteImported: boolean) => api(`/inbox${qs({ deleteImported })}`, { method: "DELETE" });
