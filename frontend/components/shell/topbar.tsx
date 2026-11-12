"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, ChevronDown, ChevronRight, CircleHelp, LogOut, Menu, Search, Settings, User } from "lucide-react";
import { findNavItem } from "@/lib/nav";
import { CURRENT_USER } from "@/lib/mock-data";
import { Popover } from "@/components/ui/popover";

const NOTIFICATIONS = [
  { id: 1, title: "Sophia Rodriguez moved to Proposal", time: "12 min ago", unread: true },
  { id: 2, title: "Follow-up overdue: Vertex Labs", time: "1 h ago", unread: true },
  { id: 3, title: "Ravi Menon — deal won ($94,000)", time: "Yesterday", unread: false },
];

export function Topbar({ onOpenNav }: { onOpenNav: () => void }) {
  const pathname = usePathname();
  const current = findNavItem(pathname);
  const searchRef = useRef<HTMLInputElement>(null);
  const unread = NOTIFICATIONS.filter((n) => n.unread).length;

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
        <label className="relative hidden md:block">
          <span className="sr-only">Search</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
          <input
            ref={searchRef}
            type="search"
            placeholder="Search leads, contacts, deals…"
            className="h-9 w-64 rounded-lg border border-line bg-canvas pr-12 pl-9 text-[13px] text-ink placeholder:text-ink-3 focus:border-brand focus:bg-surface focus:outline-none lg:w-80"
          />
          <kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded border border-line bg-surface px-1.5 py-0.5 font-mono text-[10px] text-ink-3">
            ⌘K
          </kbd>
        </label>

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
            <button className="text-[12px] font-medium text-brand hover:underline">Mark all read</button>
          </div>
          <ul className="max-h-80 divide-y divide-line overflow-y-auto">
            {NOTIFICATIONS.map((n) => (
              <li key={n.id} className="flex gap-3 px-4 py-3 hover:bg-canvas">
                <span
                  className={`mt-1.5 size-2 shrink-0 rounded-full ${n.unread ? "bg-brand" : "bg-transparent"}`}
                  aria-hidden
                />
                <div>
                  <p className="text-[13px] text-ink">{n.title}</p>
                  <p className="mt-0.5 text-[12px] text-ink-3">{n.time}</p>
                </div>
              </li>
            ))}
          </ul>
        </Popover>

        <button aria-label="Help" className="hidden size-9 place-items-center rounded-lg text-ink-2 hover:bg-canvas sm:grid">
          <CircleHelp className="size-[18px]" />
        </button>

        <span className="mx-1 hidden h-6 w-px bg-line sm:block" aria-hidden />

        <Popover
          label="Account menu"
          className="w-56 py-1.5"
          trigger={
            <span className="flex items-center gap-2 py-1 pr-1.5 pl-1">
              <span className="grid size-8 place-items-center rounded-full bg-brand-soft text-[12px] font-semibold text-brand-ink">
                {CURRENT_USER.initials}
              </span>
              <span className="hidden text-[13px] font-medium text-ink sm:block">{CURRENT_USER.name}</span>
              <ChevronDown className="hidden size-4 text-ink-3 sm:block" />
            </span>
          }
        >
          {(close) => (
            <>
              <div className="border-b border-line px-3.5 pt-1.5 pb-2.5">
                <p className="text-[13px] font-semibold">{CURRENT_USER.name}</p>
                <p className="text-[12px] text-ink-3">{CURRENT_USER.role}</p>
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
                <button className="flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] text-rose-600 hover:bg-rose-50">
                  <LogOut className="size-4" /> Log out
                </button>
              </div>
            </>
          )}
        </Popover>
      </div>
    </header>
  );
}
