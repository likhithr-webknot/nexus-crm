"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogOut, PanelLeftClose, PanelLeftOpen, X } from "lucide-react";
import { NAV_ITEMS, findNavItem } from "@/lib/nav";
import { CURRENT_USER } from "@/lib/mock-data";
import { cn } from "@/lib/format";

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
  const active = findNavItem(pathname);

  return (
    <aside className="flex h-full flex-col bg-nav text-nav-ink">
      <div className={cn("flex h-16 items-center gap-2.5 px-5", collapsed && "justify-center px-0")}>
        <span className="grid size-8 place-items-center rounded-lg bg-brand text-[15px] font-bold text-white">
          N
        </span>
        {!collapsed && (
          <span className="text-[17px] font-semibold tracking-[-0.02em] text-white">
            Nexus<span className="ml-1 text-[11px] font-medium tracking-wider text-nav-ink">CRM</span>
          </span>
        )}
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
        <ul className="space-y-0.5">
          {NAV_ITEMS.map((item) => {
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
                  {item.badge != null &&
                    (collapsed ? (
                      <span className="absolute top-2 right-3 size-1.5 rounded-full bg-indigo-400" aria-hidden />
                    ) : (
                      <span className="tnum ml-auto rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-semibold text-white">
                        {item.badge}
                      </span>
                    ))}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="border-t border-white/5 p-3">
        <div className={cn("flex items-center gap-3 rounded-lg p-2", collapsed && "justify-center")}>
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-indigo-500/20 text-[12px] font-semibold text-indigo-200">
            {CURRENT_USER.initials}
          </span>
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13px] font-semibold text-white">{CURRENT_USER.name}</p>
              <p className="truncate text-[12px]">{CURRENT_USER.role}</p>
            </div>
          )}
          {!collapsed && (
            <button aria-label="Log out" className="rounded-md p-1.5 hover:bg-nav-2 hover:text-white">
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
