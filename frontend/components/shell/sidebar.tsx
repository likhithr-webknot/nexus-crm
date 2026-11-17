"use client";

import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { Logo } from "@/components/ui/logo";
import { Tooltip } from "@/components/ui/tooltip";
import { ROLE_LABELS } from "@/lib/auth";
import { cn } from "@/lib/format";
import { leadSummary } from "@/lib/leads";
import { NAV_ITEMS, findNavItem, navItemsFor, type NavItem } from "@/lib/nav";
import { KEYS } from "@/lib/query-keys";
import { goToKeys, openPalette, openShortcuts, useModKey } from "@/lib/shortcuts";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronRight,
  ChevronsUpDown,
  Keyboard,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  Settings,
  Users,
  X,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

const GROUPS = [...new Set(NAV_ITEMS.map((item) => item.group))];
const CLOSED_GROUPS_KEY = "nexus.sidebar.closed-groups";

/* How the collapse stays steady: the sidebar has 12px of padding and every row starts with a 40px
   icon box. Collapsed, the sidebar is 64px wide, which is exactly padding + icon box, so icons never
   move. Labels stay mounted and only fade, so nothing wraps or jumps while the width animates. */
const row =
  "group relative flex h-8 w-full items-center rounded-md text-[13px] font-medium -outline-offset-2 transition-colors duration-[var(--duration-hover)]";
const rowIdle = "text-side-ink hover:bg-side-hover hover:text-side-ink-strong";
const iconBox = "grid h-8 w-10 shrink-0 place-items-center";

/** Text beside an icon. It fades out early when collapsing and fades in late when expanding. */
function labelClass(collapsed: boolean) {
  return cn(
    "min-w-0 flex-1 truncate text-left whitespace-nowrap transition-opacity",
    collapsed ? "pointer-events-none opacity-0 duration-100" : "opacity-100 delay-75 duration-150",
  );
}

function readClosedGroups(): string[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(CLOSED_GROUPS_KEY) ?? "[]");
    return Array.isArray(saved) ? saved.filter((group) => typeof group === "string") : [];
  } catch {
    return [];
  }
}

export function Sidebar({
  collapsed,
  onToggleCollapsed,
  onClose,
}: {
  collapsed: boolean;
  /** Desktop only: shows the collapse button. */
  onToggleCollapsed?: () => void;
  /** Mobile drawer only: shows the close button. */
  onClose?: () => void;
}) {
  const pathname = usePathname();
  const user = useSession();
  const mod = useModKey();
  const active = findNavItem(pathname);
  const items = navItemsFor(user.role);
  const leads = useQuery({ queryKey: KEYS.leadSummary, queryFn: leadSummary });
  // Only Leads has a count: how many are still being worked.
  const countFor = (href: string) => (href === "/leads" ? leads.data?.active : undefined);

  const [closedGroups, setClosedGroups] = useState<string[]>(readClosedGroups);
  const toggleGroup = (group: string) => {
    const next = closedGroups.includes(group)
      ? closedGroups.filter((item) => item !== group)
      : [...closedGroups, group];
    setClosedGroups(next);
    try {
      localStorage.setItem(CLOSED_GROUPS_KEY, JSON.stringify(next));
    } catch {}
  };

  return (
    <aside className="side flex h-full flex-col border-r border-side-line bg-side text-side-ink">
      <div className="flex h-14 shrink-0 items-center px-3">
        <WorkspaceMenu collapsed={collapsed} />
        {onClose && (
          <button
            onClick={onClose}
            aria-label="Close navigation"
            className="ml-1 grid size-8 shrink-0 place-items-center rounded-md hover:bg-side-hover hover:text-side-ink-strong"
          >
            <X className="size-4" aria-hidden />
          </button>
        )}
      </div>

      <div className="px-3 pb-2">
        <Tooltip label="Search" keys={[mod, "K"]} side="right" disabled={!collapsed}>
          <button
            onClick={openPalette}
            className={cn(
              row,
              rowIdle,
              !collapsed && "bg-side-active/70 shadow-[inset_0_0_0_1px_var(--side-line)] hover:bg-side-active",
            )}
          >
            <span className={iconBox}>
              <Search className="size-4" aria-hidden />
            </span>
            <span className={cn(labelClass(collapsed), "font-normal")}>Search</span>
            <span
              className={cn(
                "mr-2 flex shrink-0 gap-0.5 transition-opacity duration-100",
                collapsed ? "opacity-0" : "opacity-100 delay-75",
              )}
              aria-hidden
            >
              <kbd className="kbd">{mod}</kbd>
              <kbd className="kbd">K</kbd>
            </span>
          </button>
        </Tooltip>
      </div>

      <nav aria-label="Primary" className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-3 pb-3">
        {GROUPS.map((group, index) => {
          const groupItems = items.filter((item) => item.group === group);
          if (groupItems.length === 0) return null;
          // A collapsed sidebar has no headings to click, so every group shows.
          const open = collapsed || !closedGroups.includes(group);
          const listId = `nav-group-${group}`;
          return (
            <div key={group} className={index > 0 ? "mt-3" : undefined}>
              <div className="relative h-7">
                <button
                  onClick={() => toggleGroup(group)}
                  aria-expanded={open}
                  aria-controls={listId}
                  tabIndex={collapsed ? -1 : 0}
                  className={cn(
                    "flex h-7 w-full items-center gap-1 rounded-md pr-2 pl-3 text-[11px] font-medium tracking-[0.04em] text-side-ink uppercase -outline-offset-2 transition-opacity hover:text-side-ink-strong",
                    collapsed
                      ? "pointer-events-none opacity-0 duration-100"
                      : "opacity-80 delay-75 duration-150 hover:opacity-100",
                  )}
                >
                  <span className="whitespace-nowrap">{group}</span>
                  <ChevronRight
                    className={cn("size-3 transition-transform duration-[var(--duration-menu)]", open && "rotate-90")}
                    aria-hidden
                  />
                </button>
                {/* Collapsed: a hairline stands in for the heading, so groups stay readable. */}
                <span
                  className={cn(
                    "pointer-events-none absolute inset-x-2 top-1/2 h-px bg-side-line transition-opacity duration-150",
                    collapsed && index > 0 ? "opacity-100" : "opacity-0",
                  )}
                  aria-hidden
                />
              </div>
              {/* 0fr → 1fr animates the height without measuring it. */}
              <div
                id={listId}
                inert={!open}
                className={cn(
                  "grid transition-[grid-template-rows] duration-[var(--duration-sidebar)] ease-[var(--ease-move)]",
                  open ? "grid-rows-[1fr]" : "grid-rows-[0fr]",
                )}
              >
                <ul className="min-h-0 space-y-px overflow-hidden">
                  {groupItems.map((item) => (
                    <li key={item.href}>
                      <NavLink item={item} active={item === active} collapsed={collapsed} count={countFor(item.href)} />
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          );
        })}
      </nav>

      <div className="shrink-0 border-t border-side-line p-3">
        <UserRow collapsed={collapsed} />
        {onToggleCollapsed && (
          <Tooltip label={collapsed ? "Expand sidebar" : "Collapse sidebar"} keys={["["]} side="right">
            <button
              onClick={onToggleCollapsed}
              aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
              className={cn(row, rowIdle, "mt-1")}
            >
              <span className={iconBox}>
                {collapsed ? (
                  <PanelLeftOpen className="size-4" aria-hidden />
                ) : (
                  <PanelLeftClose className="size-4" aria-hidden />
                )}
              </span>
              <span className={cn(labelClass(collapsed), "font-normal")} aria-hidden>
                Collapse
              </span>
            </button>
          </Tooltip>
        )}
      </div>
    </aside>
  );
}

function NavLink({
  item,
  active,
  collapsed,
  count,
}: {
  item: NavItem;
  active: boolean;
  collapsed: boolean;
  count?: number;
}) {
  const Icon = item.icon;
  return (
    <Tooltip label={item.label} keys={goToKeys(item.href)} side="right" disabled={!collapsed}>
      <Link
        href={item.href}
        aria-current={active ? "page" : undefined}
        className={cn(
          row,
          active ? "bg-side-active text-side-ink-strong shadow-[inset_0_0_0_1px_var(--side-line)]" : rowIdle,
        )}
      >
        {active && (
          <span
            className="absolute inset-y-2 left-0 w-[2px] origin-center animate-[indicator-in_var(--duration-menu)_var(--ease-enter)] rounded-full bg-side-accent"
            aria-hidden
          />
        )}
        <span className={iconBox}>
          <Icon className={cn("size-4", active && "text-side-accent")} aria-hidden />
        </span>
        <span className={labelClass(collapsed)}>{item.label}</span>
        {count != null && count > 0 && (
          <>
            <span
              className={cn(
                "tnum mr-2 shrink-0 rounded px-1.5 text-[11px] leading-5 text-side-ink transition-opacity duration-100",
                collapsed ? "opacity-0" : "opacity-100 delay-75",
              )}
            >
              {count}
              <span className="sr-only"> active</span>
            </span>
            <span
              className={cn(
                "absolute top-1.5 left-[26px] size-1.5 rounded-full bg-side-accent transition-opacity duration-150",
                collapsed ? "opacity-100" : "opacity-0",
              )}
              aria-hidden
            />
          </>
        )}
      </Link>
    </Tooltip>
  );
}

/** The workspace name, with the places an admin goes to manage it. There is one workspace, so nothing to switch. */
function WorkspaceMenu({ collapsed }: { collapsed: boolean }) {
  const item =
    "flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] text-ink-2 outline-none data-[highlighted]:bg-canvas data-[highlighted]:text-ink";
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger
        aria-label="Workspace menu"
        className={cn(row, "h-10 hover:bg-side-hover data-[state=open]:bg-side-hover")}
      >
        <span className="grid h-10 w-10 shrink-0 place-items-center">
          <Logo wordmark={false} />
        </span>
        <span
          className={cn(labelClass(collapsed), "text-[14px] font-semibold tracking-[-0.01em] text-side-ink-strong")}
        >
          Nexus CRM
        </span>
        <ChevronsUpDown
          className={cn(
            "mr-2 size-3.5 shrink-0 transition-opacity duration-100",
            collapsed ? "opacity-0" : "opacity-60 delay-75",
          )}
          aria-hidden
        />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="start"
          sideOffset={6}
          className="z-50 w-56 origin-[var(--radix-dropdown-menu-content-transform-origin)] rounded-xl border border-line bg-surface p-1.5 text-ink shadow-[0_12px_32px_-8px_rgba(15,23,42,0.18)] data-[state=open]:animate-[menu-in_var(--duration-menu)_var(--ease-enter)]"
        >
          <DropdownMenu.Label className="px-2 py-1.5 text-[11px] font-medium tracking-[0.04em] text-ink-3 uppercase">
            Workspace
          </DropdownMenu.Label>
          <DropdownMenu.Item asChild>
            <Link href="/settings" className={item}>
              <Settings className="size-4" aria-hidden /> Settings
            </Link>
          </DropdownMenu.Item>
          <DropdownMenu.Item asChild>
            <Link href="/team" className={item}>
              <Users className="size-4" aria-hidden /> Team management
            </Link>
          </DropdownMenu.Item>
          <DropdownMenu.Separator className="my-1 h-px bg-line" />
          <DropdownMenu.Item onSelect={openShortcuts} className={item}>
            <Keyboard className="size-4" aria-hidden /> Keyboard shortcuts
            <kbd className="kbd ml-auto">?</kbd>
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

function UserRow({ collapsed }: { collapsed: boolean }) {
  const user = useSession();
  const { signOut, pending, failed } = useSignOut();
  return (
    <div className="flex h-10 items-center">
      <span className="grid h-10 w-10 shrink-0 place-items-center">
        <UserAvatar user={user} className="size-7" />
      </span>
      <div className={cn(labelClass(collapsed), "leading-tight")}>
        <p className="truncate text-[13px] font-medium text-side-ink-strong">{user.displayName}</p>
        <p className={cn("truncate text-[11.5px]", failed && "text-[#ff8192]")} role={failed ? "alert" : undefined}>
          {failed ? "Couldn't sign out. Try again." : ROLE_LABELS[user.role]}
        </p>
      </div>
      <Tooltip label="Sign out" side="top" disabled={collapsed}>
        <button
          onClick={() => signOut()}
          disabled={pending || collapsed}
          aria-label="Sign out"
          className={cn(
            "grid size-8 shrink-0 place-items-center rounded-md transition-opacity duration-100 hover:bg-side-hover hover:text-side-ink-strong disabled:opacity-50",
            collapsed && "pointer-events-none !opacity-0",
          )}
        >
          <LogOut className="size-4" aria-hidden />
        </button>
      </Tooltip>
    </div>
  );
}
