/* The recycle bin (backend: TrashController). Managers and admins only. */

import { api, qs } from "@/lib/api";

export type TrashType = "LEAD" | "CONTACT" | "COMPANY" | "DEAL" | "CAMPAIGN" | "PRODUCT" | "QUOTE" | "COMMUNICATION" | "AUTOMATION";

export type TrashItem = { type: TrashType; id: string; name: string; deletedAt: string; ownerName: string | null };

export const TRASH_LABELS: Record<TrashType, string> = { LEAD: "Lead", CONTACT: "Contact", COMPANY: "Company", DEAL: "Deal", CAMPAIGN: "Campaign", PRODUCT: "Product", QUOTE: "Quote", COMMUNICATION: "Communication", AUTOMATION: "Automation" };

/** `total` counts every deleted record you can see; `items` is the newest of them, so it can be shorter. */
export type TrashPage = { items: TrashItem[]; total: number; retentionDays: number };

export const listTrash = (type?: TrashType) => api<TrashPage>(`/trash${qs({ type })}`);

/** Brings a deleted record back. The API refuses with a reason when that would break a rule. */
export const restoreFromTrash = (type: TrashType, id: string) => api(`/trash/${type}/${id}/restore`, { method: "POST" });
