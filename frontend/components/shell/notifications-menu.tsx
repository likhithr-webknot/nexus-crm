"use client";

import { Popover } from "@/components/ui/popover";
import { useRefresh } from "@/hooks/use-refresh";
import { cn, localIso } from "@/lib/format";
import { listNotifications, markRead, unreadCount } from "@/lib/notifications";
import { KEYS } from "@/lib/query-keys";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import Link from "next/link";

/** "12 min ago", "3 h ago", or the date for anything older than a day. */
function timeAgo(instant: string) {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(instant).getTime()) / 60_000));
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 24 * 60) return `${Math.round(minutes / 60)} h ago`;
  return localIso(instant).slice(0, 10);
}

/** The unread count is checked every minute, and the list loads when it is needed. */
function useNotifications() {
  const refresh = useRefresh();
  const count = useQuery({ queryKey: [...KEYS.notifications, "count"], queryFn: unreadCount, refetchInterval: 60_000 });
  const list = useQuery({ queryKey: [...KEYS.notifications, "list"], queryFn: listNotifications, staleTime: 30_000 });
  const read = useMutation({ mutationFn: (ids?: string[]) => markRead(ids), onSuccess: () => refresh([KEYS.notifications]) });
  return { unread: count.data?.count ?? 0, list, read };
}

/** The bell with its unread dot, and the menu of recent notifications. */
export function NotificationsMenu() {
  const { unread, list, read } = useNotifications();

  return (
    <Popover
      label={`Notifications, ${unread} unread`}
      className="w-80"
      trigger={
        <span className="relative grid size-9 place-items-center text-ink-2">
          <Bell className="size-[18px]" />
          {unread > 0 && <span className="absolute top-2 right-2 size-2 rounded-full bg-danger ring-2 ring-surface" />}
        </span>
      }
    >
      <div className="flex items-center justify-between border-b border-line px-4 py-3">
        <p className="text-[14px] font-semibold">Notifications</p>
        <button
          onClick={() => read.mutate(undefined)}
          disabled={unread === 0 || read.isPending}
          className="text-[12px] font-medium text-brand hover:underline disabled:text-ink-3 disabled:no-underline"
        >
          Mark all read
        </button>
      </div>
      {list.isPending ? (
        <p className="px-4 py-6 text-center text-[13px] text-ink-3">Loading…</p>
      ) : list.isError ? (
        <p className="px-4 py-6 text-center text-[13px] text-down">Couldn&apos;t load notifications.</p>
      ) : list.data.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-ink-3">You&apos;re all caught up.</p>
      ) : (
        <ul className="max-h-80 divide-y divide-line overflow-y-auto">
          {list.data.map((n) => {
            const markAsRead = () => !n.read && read.mutate([n.id]);
            const body = (
              <>
                <span className={cn("mt-1.5 size-2 shrink-0 rounded-full", n.read ? "bg-transparent" : "bg-brand")} aria-hidden />
                <div>
                  <p className="text-[13px] text-ink">{n.title}</p>
                  <p className="mt-0.5 text-[12px] text-ink-3">{timeAgo(n.createdAt)}</p>
                </div>
              </>
            );
            return (
              <li key={n.id}>
                {n.link ? (
                  <Link href={n.link} onClick={markAsRead} className="flex gap-3 px-4 py-3 hover:bg-canvas">
                    {body}
                  </Link>
                ) : (
                  <button onClick={markAsRead} className="flex w-full gap-3 px-4 py-3 text-left hover:bg-canvas">
                    {body}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Popover>
  );
}
