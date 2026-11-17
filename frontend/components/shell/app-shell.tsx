"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { Toaster } from "@/components/ui/toast";
import { TooltipProvider } from "@/components/ui/tooltip";
import { DURATION, EASE } from "@/lib/motion";
import { toggleSidebar, useSidebarCollapsed } from "@/lib/sidebar-state";
import { CREATE, GO_TO, openCreate, openPalette, openShortcuts } from "@/lib/shortcuts";
import { motion, useReducedMotion } from "motion/react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { CommandPalette } from "./command-palette";
import { Sidebar } from "./sidebar";
import { Topbar } from "./topbar";

const SIDEBAR_WIDTH = { expanded: 248, collapsed: 64 };

/** True while the user is typing somewhere, so single-key shortcuts must stay out of the way. */
function isTyping(target: EventTarget | null) {
  const element = target as HTMLElement | null;
  return Boolean(element && (element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName)));
}

/**
 * Single-key shortcuts: "G then L" goes to Leads, "N then L" starts a lead, "[" folds the sidebar,
 * "/" searches, "?" lists them all. Ignored while typing, with a modifier held, or while a dialog is open.
 */
function useGlobalShortcuts() {
  const router = useRouter();
  useEffect(() => {
    let prefix: string | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const onKey = (event: KeyboardEvent) => {
      if (event.metaKey || event.ctrlKey || event.altKey || isTyping(event.target)) return;
      if (document.querySelector('dialog[open], [role="dialog"]')) return;
      const key = event.key.toLowerCase();

      if (prefix) {
        const first = prefix;
        prefix = null;
        clearTimeout(timer);
        if (first === "g" && GO_TO[key]) {
          event.preventDefault();
          router.push(GO_TO[key].href);
        } else if (first === "n" && CREATE[key]) {
          event.preventDefault();
          openCreate(CREATE[key].kind);
        }
        return;
      }
      if (key === "g" || key === "n") {
        prefix = key;
        timer = setTimeout(() => (prefix = null), 1200);
      } else if (event.key === "[") {
        toggleSidebar();
      } else if (event.key === "/") {
        event.preventDefault();
        openPalette();
      } else if (event.key === "?") {
        openShortcuts();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      clearTimeout(timer);
    };
  }, [router]);
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const { rateState } = useCurrency();
  const collapsed = useSidebarCollapsed();
  const pathname = usePathname();
  const reducedMotion = useReducedMotion();
  // The drawer is open only for the route it was opened on, so navigating closes it.
  const [drawerPath, setDrawerPath] = useState<string | null>(null);
  const mobileOpen = drawerPath === pathname;
  const setMobileOpen = (open: boolean) => setDrawerPath(open ? pathname : null);
  useGlobalShortcuts();

  return (
    <TooltipProvider>
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[80] focus:rounded-md focus:bg-surface focus:px-3 focus:py-2 focus:text-[13px] focus:font-medium focus:shadow-lg"
      >
        Skip to content
      </a>
      <div className="flex h-full">
        {/* Desktop. Width is animated once per toggle, not during any high-frequency interaction. */}
        <motion.div
          className="hidden shrink-0 overflow-hidden lg:block"
          initial={false}
          animate={{ width: collapsed ? SIDEBAR_WIDTH.collapsed : SIDEBAR_WIDTH.expanded }}
          transition={{ duration: reducedMotion ? 0 : DURATION.sidebar, ease: EASE.enter }}
        >
          <Sidebar collapsed={collapsed} onToggleCollapsed={toggleSidebar} />
        </motion.div>

        {/* Tablet and phone: the same sidebar as a drawer. */}
        {mobileOpen && (
          <div
            className="fixed inset-0 z-50 lg:hidden"
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            onKeyDown={(event) => event.key === "Escape" && setMobileOpen(false)}
          >
            <button
              aria-label="Close navigation"
              className="absolute inset-0 animate-[fade-in_var(--duration-menu)_ease-out] bg-scrim"
              onClick={() => setMobileOpen(false)}
            />
            <div className="relative h-full w-[272px] max-w-[85vw] animate-[drawer-left-in_var(--duration-sidebar)_var(--ease-enter)] shadow-2xl">
              <Sidebar collapsed={false} onClose={() => setMobileOpen(false)} />
            </div>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <Topbar onOpenNav={() => setMobileOpen(true)} />
          {rateState.status === "error" && (
            <div
              role="alert"
              className="tone-amber border-b border-[var(--tone-line)] bg-[var(--tone-bg)] px-4 py-2 text-[12.5px] text-[var(--tone-ink)] sm:px-6"
            >
              Exchange rates could not be loaded; converted totals are hidden.{" "}
              <Link href="/settings?section=currency" className="font-semibold underline">
                Check currency settings
              </Link>
            </div>
          )}
          <main id="main" tabIndex={-1} className="flex-1 overflow-y-auto outline-none">
            {/* Only the changing content fades in. Keyed by section ("/quotes"), so opening a record
                beside its list ("/quotes/123") does not restart it. */}
            <div key={pathname.split("/")[1]} className="animate-[page-in_var(--duration-page)_var(--ease-enter)]">
              {children}
            </div>
          </main>
        </div>
        <CommandPalette />
        <Toaster />
      </div>
    </TooltipProvider>
  );
}
