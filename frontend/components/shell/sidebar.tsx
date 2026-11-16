"use client";

import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { Logo } from "@/components/ui/logo";
import { ROLE_LABELS } from "@/lib/auth";
import { cn } from "@/lib/format";
import { leadSummary } from "@/lib/leads";
import { NAV_ITEMS, findNavItem, navItemsFor } from "@/lib/nav";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { LogOut, PanelLeftClose, PanelLeftOpen, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

const GROUPS = [...new Set(NAV_ITEMS.map((item) => item.group))];

export function Sidebar({
  collapsed,
  onToggleCollapsed,
  onClose,
}: {
  collapsed: boolean;
  onToggleCollapsed?: () => void;
  onClose?: () => void;
}) {
  const pathname = usePathname();
  const leads = useQuery({ queryKey: KEYS.leadSummary, queryFn: leadSummary });
  /** The number shown beside a menu item. Only Leads has one: how many are still being worked. */
  const badge = (href: string) => (href === "/leads" ? leads.data?.active : undefined);
  const active = findNavItem(pathname);
  const user = useSession();
  const { signOut, pending, failed } = useSignOut();

  return (
    <aside className="flex h-full flex-col bg-nav text-nav-ink">
      <div className={cn("flex h-16 items-center gap-2.5 px-5", collapsed && "justify-center px-0")}>
        <Logo tone="dark" wordmark={!collapsed} />
        {onClose && (
          <button
            onClick={onClose}
            aria-label="Close navigation"
            className="ml-auto rounded-md p-1.5 hover:bg-nav-2 hover:text-white"
          >
            <X className="size-4" />
          </button>
        )}
      </div>

      <nav aria-label="Primary" className="flex-1 overflow-y-auto px-3 py-3">
        {GROUPS.map((group, gi) => (
          <div key={group} className={gi > 0 ? "mt-5" : undefined}>
            {collapsed ? (
              gi > 0 && <div className="mx-3 mb-3 border-t border-white/10" aria-hidden />
            ) : (
              <p className="mb-1.5 px-3 text-[10.5px] font-semibold tracking-[0.08em] text-nav-ink/60 uppercase">{group}</p>
            )}
            <ul className="space-y-0.5">
              {navItemsFor(user.role).filter((item) => item.group === group).map((item) => {
                const isActive = item === active;
                const Icon = item.icon;
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      aria-current={isActive ? "page" : undefined}
                      title={collapsed ? item.label : undefined}
                      className={cn(
                        "group relative flex h-10 items-center gap-3 rounded-lg px-3 text-[14px] font-medium transition-colors",
                        collapsed && "justify-center px-0",
                        isActive ? "bg-nav-2 text-white" : "hover:bg-nav-2/60 hover:text-white",
                      )}
                    >
                      {isActive && (
                        <span className="absolute inset-y-2 left-0 w-[3px] rounded-r bg-brand" aria-hidden />
                      )}
                      <Icon className={cn("size-[18px] shrink-0", isActive && "text-indigo-300")} aria-hidden />
                      {!collapsed && <span className="truncate">{item.label}</span>}
                      {badge(item.href) != null &&
                        (collapsed ? (
                          <span className="absolute top-2 right-3 size-1.5 rounded-full bg-indigo-400" aria-hidden />
                        ) : (
                          <span className="tnum ml-auto rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-semibold text-white">
                            {badge(item.href)}
                          </span>
                        ))}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-white/5 p-3">
        <div className={cn("flex items-center gap-3 rounded-lg p-2", collapsed && "justify-center")}>
          <UserAvatar user={user} className="size-9" />
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13px] font-semibold text-white">{user.displayName}</p>
              <p className={cn("truncate text-[12px]", failed && "text-rose-300")} role={failed ? "alert" : undefined}>
                {failed ? "Couldn't sign out. Try again." : ROLE_LABELS[user.role]}
              </p>
            </div>
          )}
          {!collapsed && (
            <button
              onClick={() => signOut()}
              disabled={pending}
              aria-label="Sign out"
              title="Sign out"
              className="rounded-md p-1.5 hover:bg-nav-2 hover:text-white disabled:opacity-50"
            >
              <LogOut className="size-4" />
            </button>
          )}
        </div>
        {onToggleCollapsed && (
          <button
            onClick={onToggleCollapsed}
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            className={cn(
              "mt-1 flex h-9 w-full items-center gap-3 rounded-lg px-3 text-[13px] hover:bg-nav-2 hover:text-white",
              collapsed && "justify-center px-0",
            )}
          >
            {collapsed ? <PanelLeftOpen className="size-4" /> : <PanelLeftClose className="size-4" />}
            {!collapsed && "Collapse"}
          </button>
        )}
      </div>
    </aside>
  );
}
