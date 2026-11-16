/* In-app notifications (backend: NotificationController) and the top-bar search. */

import { api, qs } from "@/lib/api";

export type AppNotification = { id: string; type: string; title: string; link: string | null; read: boolean; createdAt: string };

export const listNotifications = () => api<AppNotification[]>("/notifications?size=20");

export const unreadCount = () => api<{ count: number }>("/notifications/unread-count");

/** Marks the given notifications as read, or all of them when no IDs are given. */
export const markRead = (ids?: string[]) => api("/notifications/read", { method: "POST", body: JSON.stringify({ ids }) });

export type SearchHit = { type: string; id: string; title: string; subtitle: string | null; href: string };

export const search = (q: string) => api<SearchHit[]>(`/search${qs({ q })}`);
