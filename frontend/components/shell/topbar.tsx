"use client";

import { findNavItem } from "@/lib/nav";
import { ChevronRight, Menu } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AccountMenu } from "./account-menu";
import { GlobalSearch } from "./global-search";
import { NotificationsMenu } from "./notifications-menu";
import { ShortcutsHelp } from "./shortcuts-help";

/** The bar across the top: breadcrumb on the left; search, notifications, help and the account menu on the right. */
export function Topbar({ onOpenNav }: { onOpenNav: () => void }) {
  const current = findNavItem(usePathname());

  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur sm:px-6">
      <button onClick={onOpenNav} aria-label="Open navigation" className="-ml-1 rounded-lg p-2 text-ink-2 hover:bg-canvas lg:hidden">
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
        <GlobalSearch />
        <NotificationsMenu />
        <ShortcutsHelp />
        <span className="mx-1 hidden h-6 w-px bg-line sm:block" aria-hidden />
        <AccountMenu />
      </div>
    </header>
  );
}
