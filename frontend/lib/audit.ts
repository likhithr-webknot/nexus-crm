/* The audit log (backend: AuditController). Admins only. */

import { api, qs } from "@/lib/api";
import type { PageResponse } from "@/lib/page";

export type AuditEntry = {
  id: number;
  occurredAt: string;
  actorId: string | null;
  actorName: string | null;
  entityType: string;
  entityId: string;
  action: "CREATE" | "UPDATE" | "DELETE" | "RESTORE" | "CONVERT";
  /** Each changed field with its old and new value. */
  changes: Record<string, [unknown, unknown]> | null;
};

export const listAudit = (params: { entityType?: string; page?: number; size?: number }) =>
  api<PageResponse<AuditEntry>>(`/audit${qs(params)}`);
