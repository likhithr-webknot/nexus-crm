"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, ChevronDown, ChevronRight, CircleHelp, LogOut, Menu, Search, Settings, User } from "lucide-react";
import { findNavItem } from "@/lib/nav";
import { ROLE_LABELS } from "@/lib/auth";
import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { Dialog, DialogBody } from "@/components/ui/dialog";
import { Popover } from "@/components/ui/popover";
import { listNotifications, markRead, search, unreadCount } from "@/lib/notifications";
import { cn, localIso } from "@/lib/format";

/** "12 min ago", "3 h ago", or the date for anything older than a day. */
function timeAgo(instant: string) {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(instant).getTime()) / 60_000));
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 24 * 60) return `${Math.round(minutes / 60)} h ago`;
  return localIso(instant).slice(0, 10);
}

/** The bell: the unread count is checked every minute, and the list loads when the menu opens. */
function useNotifications() {
  const queryClient = useQueryClient();
  const count = useQuery({ queryKey: ["notifications", "count"], queryFn: unreadCount, refetchInterval: 60_000 });
  const list = useQuery({ queryKey: ["notifications", "list"], queryFn: listNotifications, staleTime: 30_000 });
  const read = useMutation({
    mutationFn: (ids?: string[]) => markRead(ids),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
  return { unread: count.data?.count ?? 0, list, read };
}

const SHORTCUTS: [keys: string[], what: string][] = [
  [["⌘", "K"], "Jump to search (Ctrl K on Windows and Linux)"],
  [["←", "→"], "Move the focused deal to the previous or next stage"],
  [["Esc"], "Close a dialog or menu"],
  [["Tab"], "Move to the next control"],
];

export function Topbar({ onOpenNav }: { onOpenNav: () => void }) {
  const pathname = usePathname();
  const current = findNavItem(pathname);
  const searchRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [helpOpen, setHelpOpen] = useState(false);
  const { unread, list, read } = useNotifications();
  const term = query.trim();
  const hits = useQuery({ queryKey: ["search", term], queryFn: () => search(term), enabled: term.length >= 2, staleTime: 30_000 });
  const user = useSession();
  const { signOut, pending, failed } = useSignOut();

  // ⌘K / Ctrl+K focuses search.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur sm:px-6">
      <button
        onClick={onOpenNav}
        aria-label="Open navigation"
        className="-ml-1 rounded-lg p-2 text-ink-2 hover:bg-canvas lg:hidden"
      >
        <Menu className="size-5" />
      </button>

      <nav aria-label="Breadcrumb" className="hidden items-center gap-1.5 text-[13px] sm:flex">
        <Link href="/" className="text-ink-3 hover:text-ink">
          Nexus CRM
        </Link>
        <ChevronRight className="size-3.5 text-ink-3" aria-hidden />
        <span className="font-medium text-ink">{current?.label ?? "Not found"}</span>
      </nav>

      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        <div className="relative hidden md:block">
          <label>
            <span className="sr-only">Search</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Escape" && setQuery("")}
              placeholder="Search leads, contacts, deals…"
              className="h-9 w-64 rounded-lg border border-line bg-canvas pr-12 pl-9 text-[13px] text-ink placeholder:text-ink-3 focus:border-brand focus:bg-surface focus:outline-none lg:w-80"
            />
            <kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded border border-line bg-surface px-1.5 py-0.5 font-mono text-[10px] text-ink-3">
              ⌘K
            </kbd>
          </label>
          {term.length >= 2 && (
            <div className="absolute top-11 left-0 z-40 max-h-96 w-full min-w-80 overflow-y-auto rounded-xl border border-line bg-surface py-1.5 shadow-lg" role="listbox" aria-label="Search results">
              {hits.isPending ? (
                <p className="px-4 py-3 text-[13px] text-ink-3">Searching…</p>
              ) : hits.isError ? (
                <p className="px-4 py-3 text-[13px] text-down">Search isn&apos;t available right now.</p>
              ) : hits.data.length === 0 ? (
                <p className="px-4 py-3 text-[13px] text-ink-3">Nothing matches &ldquo;{term}&rdquo;.</p>
              ) : (
                hits.data.map((hit) => (
                  <Link
                    key={`${hit.type}-${hit.id}`}
                    href={hit.href}
                    onClick={() => setQuery("")}
                    role="option"
                    aria-selected={false}
                    className="flex items-baseline gap-3 px-4 py-2 hover:bg-canvas"
                  >
                    <span className="w-14 shrink-0 text-[11px] font-semibold tracking-wide text-ink-3 uppercase">{hit.type}</span>
                    <span className="min-w-0">
                      <span className="block truncate text-[13px] font-medium text-ink">{hit.title}</span>
                      {hit.subtitle && <span className="block truncate text-[12px] text-ink-3">{hit.subtitle}</span>}
                    </span>
                  </Link>
                ))
              )}
            </div>
          )}
        </div>

        <Popover
          label={`Notifications, ${unread} unread`}
          className="w-80"
          trigger={
            <span className="relative grid size-9 place-items-center text-ink-2">
              <Bell className="size-[18px]" />
              {unread > 0 && (
                <span className="absolute top-2 right-2 size-2 rounded-full bg-rose-500 ring-2 ring-surface" />
              )}
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
                      <Link href={n.link} onClick={() => !n.read && read.mutate([n.id])} className="flex gap-3 px-4 py-3 hover:bg-canvas">
                        {body}
                      </Link>
                    ) : (
                      <button onClick={() => !n.read && read.mutate([n.id])} className="flex w-full gap-3 px-4 py-3 text-left hover:bg-canvas">
                        {body}
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </Popover>

        <button
          aria-label="Help"
          onClick={() => setHelpOpen(true)}
          className="hidden size-9 place-items-center rounded-lg text-ink-2 hover:bg-canvas sm:grid"
        >
          <CircleHelp className="size-[18px]" />
        </button>

        <span className="mx-1 hidden h-6 w-px bg-line sm:block" aria-hidden />

        <Popover
          label="Account menu"
          className="w-60 py-1.5"
          trigger={
            <span className="flex items-center gap-2 py-1 pr-1.5 pl-1">
              <UserAvatar user={user} />
              <span className="hidden max-w-40 truncate text-[13px] font-medium text-ink sm:block">{user.displayName}</span>
              <ChevronDown className="hidden size-4 text-ink-3 sm:block" />
            </span>
          }
        >
          {(close) => (
            <>
              <div className="border-b border-line px-3.5 pt-1.5 pb-2.5">
                <p className="truncate text-[13px] font-semibold">{user.displayName}</p>
                <p className="truncate text-[12px] text-ink-3">{user.email}</p>
                <p className="mt-1 text-[12px] text-ink-3">{ROLE_LABELS[user.role]}</p>
              </div>
              <div className="p-1.5">
                {[
                  { href: "/settings", label: "Profile", icon: User },
                  { href: "/settings", label: "Settings", icon: Settings },
                ].map(({ href, label, icon: Icon }) => (
                  <Link
                    key={label}
                    href={href}
                    onClick={close}
                    className="flex items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] text-ink-2 hover:bg-canvas hover:text-ink"
                  >
                    <Icon className="size-4" /> {label}
                  </Link>
                ))}
                <button
                  onClick={() => signOut()}
                  disabled={pending}
                  className="flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                >
                  <LogOut className="size-4" /> {pending ? "Signing out…" : "Sign out"}
                </button>
                {failed && (
                  <p role="alert" className="px-2 pt-1 text-[12px] text-rose-600">
                    Couldn&apos;t reach the server. Try again.
                  </p>
                )}
              </div>
            </>
          )}
        </Popover>
      </div>

      <Dialog open={helpOpen} onClose={() => setHelpOpen(false)} title="Keyboard shortcuts" size="sm">
        <DialogBody>
          <dl className="divide-y divide-line text-[13px]">
            {SHORTCUTS.map(([keys, what]) => (
              <div key={what} className="flex items-center justify-between gap-4 py-2.5">
                <dt className="text-ink-2">{what}</dt>
                <dd className="flex shrink-0 gap-1">
                  {keys.map((k) => (
                    <kbd key={k} className="rounded border border-line bg-canvas px-1.5 py-0.5 font-mono text-[11px] text-ink-2">
                      {k}
                    </kbd>
                  ))}
                </dd>
              </div>
            ))}
          </dl>
        </DialogBody>
      </Dialog>
    </header>
  );
}
