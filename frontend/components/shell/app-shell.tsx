"use client";

import { useState, useSyncExternalStore } from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "./sidebar";
import { Topbar } from "./topbar";
import { Toaster } from "@/components/ui/toast";
import { cn } from "@/lib/format";
import { useCurrency } from "@/components/currency/currency-provider";
import Link from "next/link";

const COLLAPSE_KEY = "nexus.sidebar.collapsed";
const listeners = new Set<() => void>();

// Sidebar preference lives in localStorage; server snapshot is "expanded".
const collapseStore = {
  subscribe(cb: () => void) {
    listeners.add(cb);
    return () => listeners.delete(cb);
  },
  get() {
    try {
      return localStorage.getItem(COLLAPSE_KEY) === "1";
    } catch {
      return false;
    }
  },
  set(value: boolean) {
    try {
      localStorage.setItem(COLLAPSE_KEY, value ? "1" : "0");
    } catch {}
    listeners.forEach((l) => l());
  },
};

export function AppShell({ children }: { children: React.ReactNode }) {
  const { rateState } = useCurrency();
  const collapsed = useSyncExternalStore(collapseStore.subscribe, collapseStore.get, () => false);
  const pathname = usePathname();
  // Drawer is open only for the route it was opened on, so navigating closes it.
  const [drawerPath, setDrawerPath] = useState<string | null>(null);
  const mobileOpen = drawerPath === pathname;
  const setMobileOpen = (open: boolean) => setDrawerPath(open ? pathname : null);

  const toggleCollapsed = () => collapseStore.set(!collapsed);

  return (
    <div className="flex h-full">
      {/* Desktop */}
      <div
        className={cn(
          "hidden shrink-0 transition-[width] duration-200 lg:block",
          collapsed ? "w-[76px]" : "w-[248px]",
        )}
      >
        <Sidebar collapsed={collapsed} onToggleCollapsed={toggleCollapsed} />
      </div>

      {/* Tablet / mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <button
            aria-label="Close navigation"
            className="absolute inset-0 bg-slate-950/40"
            onClick={() => setMobileOpen(false)}
          />
          <div className="relative h-full w-[264px] shadow-2xl">
            <Sidebar collapsed={false} onClose={() => setMobileOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenNav={() => setMobileOpen(true)} />
        {rateState.status === "error" && (
          <div role="alert" className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-[12.5px] text-amber-900 sm:px-6">
            Exchange rates could not be loaded; converted totals are hidden.{" "}
            <Link href="/settings?section=currency" className="font-semibold underline">
              Check currency settings
            </Link>
          </div>
        )}
        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
      <Toaster />
    </div>
  );
}
