/* Tags and bulk tagging (backend: TagController and the bulk-tags endpoints). */

import { api } from "@/lib/api";

export const TAG_COLORS = ["neutral", "brand", "violet", "green", "amber", "red"] as const;
export type TagColor = (typeof TAG_COLORS)[number];

export type Tag = { id: string; name: string; color: TagColor };

/** The list pages that support tags. The value is the API path. */
export type TagKind = "leads" | "contacts" | "deals";

export const listTags = () => api<Tag[]>("/tags");

export const createTag = (name: string, color: TagColor = "neutral") =>
  api<Tag>("/tags", { method: "POST", body: JSON.stringify({ name, color }) });

export const deleteTag = (id: string) => api(`/tags/${id}`, { method: "DELETE" });

export const tagRecords = (kind: TagKind, body: { ids: string[]; add?: string[]; remove?: string[] }) =>
  api<{ count: number }>(`/${kind}/bulk-tags`, { method: "POST", body: JSON.stringify(body) });

/** Deletes several contacts or deals at once. All of them are deleted, or none. */
export const deleteRecords = (kind: Exclude<TagKind, "leads">, ids: string[]) =>
  api(`/${kind}/bulk-delete`, { method: "POST", body: JSON.stringify({ ids }) });
