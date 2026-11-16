"use client";

import { Tooltip } from "@/components/ui/tooltip";
import { findNavItem } from "@/lib/nav";
import { openPalette, useModKey } from "@/lib/shortcuts";
import { useTheme } from "@/lib/theme";
import { ChevronRight, Menu, Moon, Search, Sun } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AccountMenu } from "./account-menu";
import { AiDrawer } from "./ai-drawer";
import { NotificationsMenu } from "./notifications-menu";
import { QuickCreate } from "./quick-create";
import { ShortcutsHelp } from "./shortcuts-help";

export const topbarIconButton =
  "grid size-8 place-items-center rounded-md text-ink-2 transition-colors duration-[var(--duration-hover)] hover:bg-surface-muted hover:text-ink";

/** The bar across the top: where you are on the left; search, create and account on the right. */
export function Topbar({ onOpenNav }: { onOpenNav: () => void }) {
  const pathname = usePathname();
  const current = findNavItem(pathname);
  const { theme, setTheme } = useTheme();
  const mod = useModKey();
  // On a record page ("/leads/123") the module name links back to its list.
  const onRecord = Boolean(current && current.href !== "/" && pathname !== current.href);

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b border-line bg-surface/90 px-3 backdrop-blur sm:px-5">
      <button onClick={onOpenNav} aria-label="Open navigation" className={`${topbarIconButton} lg:hidden`}>
        <Menu className="size-[18px]" aria-hidden />
      </button>

      <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1.5 text-[13px]">
        {current && <span className="hidden text-ink-3 sm:inline">{current.group}</span>}
        {current && <ChevronRight className="hidden size-3.5 shrink-0 text-ink-3 sm:inline" aria-hidden />}
        {onRecord && current ? (
          <>
            <Link href={current.href} className="truncate rounded text-ink-2 hover:text-ink">
              {current.label}
            </Link>
            <ChevronRight className="size-3.5 shrink-0 text-ink-3" aria-hidden />
            <span className="truncate font-medium text-ink" aria-current="page">
              Details
            </span>
          </>
        ) : (
          <span className="truncate font-medium text-ink" aria-current="page">
            {current?.label ?? "Not found"}
          </span>
        )}
      </nav>

      <div className="ml-auto flex items-center gap-1">
        {/* From lg up the sidebar carries search. */}
        <button
          onClick={openPalette}
          className="hidden h-8 w-52 items-center gap-2 rounded-md border border-line bg-canvas px-2.5 text-[12.5px] text-ink-3 transition-colors duration-[var(--duration-hover)] hover:border-line-strong hover:text-ink-2 md:flex lg:hidden"
        >
          <Search className="size-4" aria-hidden />
          Search
          <span className="ml-auto flex gap-0.5" aria-hidden>
            <kbd className="kbd">{mod}</kbd>
            <kbd className="kbd">K</kbd>
          </span>
        </button>
        <button onClick={openPalette} aria-label="Search" className={`${topbarIconButton} md:hidden`}>
          <Search className="size-[17px]" aria-hidden />
        </button>
        <QuickCreate />
        <AiDrawer />
        <NotificationsMenu />
        <ShortcutsHelp />
        <Tooltip label={theme === "light" ? "Dark theme" : "Light theme"}>
          <button onClick={() => setTheme(theme === "light" ? "dark" : "light")} aria-label={`Switch to ${theme === "light" ? "dark" : "light"} theme`} className={topbarIconButton}>
            {theme === "light" ? <Moon className="size-[17px]" aria-hidden /> : <Sun className="size-[17px]" aria-hidden />}
          </button>
        </Tooltip>
        <span className="mx-1.5 hidden h-5 w-px bg-line sm:block" aria-hidden />
        <AccountMenu />
      </div>
    </header>
  );
}
