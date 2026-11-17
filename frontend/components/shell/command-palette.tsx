"use client";

import { useSession } from "@/components/auth/session";
import { useDebounced } from "@/hooks/use-debounced";
import { cn } from "@/lib/format";
import { navItemsFor } from "@/lib/nav";
import { search, type SearchHit } from "@/lib/notifications";
import { readRecents, rememberRecent, type Recent } from "@/lib/recents";
import { toggleSidebar, useSidebarCollapsed } from "@/lib/sidebar-state";
import { CREATE, SHELL_EVENTS, createKeys, goToKeys, openCreate, openShortcuts } from "@/lib/shortcuts";
import { useTheme } from "@/lib/theme";
import * as Dialog from "@radix-ui/react-dialog";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  Contact,
  CornerDownLeft,
  Handshake,
  History,
  Keyboard,
  Moon,
  PanelLeft,
  Plus,
  Search,
  Sun,
  UserPlus,
  type LucideIcon,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";

type Entry = {
  key: string;
  section: "Records" | "Recent" | "Go to" | "Create" | "Actions";
  label: string;
  detail?: string;
  icon: LucideIcon;
  /** Key caps for the same action outside the palette. */
  keys?: string[];
  run: () => void;
};

const SECTIONS: Entry["section"][] = ["Records", "Recent", "Go to", "Create", "Actions"];
const RECORD_FILTERS = ["All", "Lead", "Contact", "Company", "Deal"];
const RECORD_ICONS: Record<string, LucideIcon> = {
  lead: UserPlus,
  contact: Contact,
  company: Building2,
  deal: Handshake,
};
const recordIcon = (type: string) => RECORD_ICONS[type.toLowerCase()] ?? Search;

/** The text with the part that matches the query marked. */
function Highlight({ text, query }: { text: string; query: string }) {
  const at = query ? text.toLowerCase().indexOf(query) : -1;
  if (at < 0) return text;
  return (
    <>
      {text.slice(0, at)}
      <mark className="rounded-[3px] bg-brand-soft text-brand-ink">{text.slice(at, at + query.length)}</mark>
      {text.slice(at + query.length)}
    </>
  );
}

/**
 * Search, navigate and act from the keyboard. Opens with ⌘K / Ctrl K from anywhere, or when
 * something dispatches the "nexus:palette" event. Mounted once, in the app shell.
 */
export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("All");
  const [active, setActive] = useState(0);
  const [recents, setRecents] = useState<Recent[]>([]);
  const listId = useId();
  const router = useRouter();
  const user = useSession();
  const { theme, setTheme } = useTheme();
  const sidebarCollapsed = useSidebarCollapsed();

  const q = query.trim().toLowerCase();
  const term = useDebounced(q, 180);
  const searching = q.length >= 2;
  const hits = useQuery({
    queryKey: ["command-search", term],
    queryFn: () => search(term),
    enabled: open && term.length >= 2,
    staleTime: 30_000,
  });

  const show = () => {
    setRecents(readRecents());
    setOpen(true);
  };
  const close = () => {
    setOpen(false);
    setQuery("");
    setFilter("All");
    setActive(0);
  };

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setRecents(readRecents());
        setOpen((value) => !value);
      }
    };
    const onOpen = () => {
      setRecents(readRecents());
      setOpen(true);
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener(SHELL_EVENTS.palette, onOpen);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(SHELL_EVENTS.palette, onOpen);
    };
  }, []);

  const go = (href: string) => {
    close();
    router.push(href);
  };
  const openRecord = (record: Recent) => {
    rememberRecent(record);
    go(record.href);
  };
  const matches = (text: string) => !q || text.toLowerCase().includes(q);

  const entries: Entry[] = [
    ...(searching ? (hits.data ?? []) : [])
      .filter((hit: SearchHit) => filter === "All" || hit.type.toLowerCase() === filter.toLowerCase())
      .slice(0, 8)
      .map((hit: SearchHit): Entry => ({
        key: `record-${hit.type}-${hit.id}`,
        section: "Records",
        label: hit.title,
        detail: [hit.type, hit.subtitle].filter(Boolean).join(" · "),
        icon: recordIcon(hit.type),
        run: () => openRecord({ href: hit.href, title: hit.title, type: hit.type }),
      })),
    ...(q ? [] : recents).map((recent): Entry => ({
      key: `recent-${recent.href}`,
      section: "Recent",
      label: recent.title,
      detail: recent.type,
      icon: History,
      run: () => openRecord(recent),
    })),
    ...navItemsFor(user.role)
      .filter((item) => matches(item.label))
      .map((item): Entry => ({
        key: `go-${item.href}`,
        section: "Go to",
        label: item.label,
        icon: item.icon,
        keys: goToKeys(item.href),
        run: () => go(item.href),
      })),
    ...Object.values(CREATE)
      .filter(({ label }) => matches(`create new ${label}`))
      .map(({ kind, label }): Entry => ({
        key: `create-${kind}`,
        section: "Create",
        label: `New ${label.toLowerCase()}`,
        icon: Plus,
        keys: createKeys(kind),
        run: () => {
          close();
          openCreate(kind);
        },
      })),
    ...(
      [
        {
          key: "theme",
          label: theme === "light" ? "Switch to dark theme" : "Switch to light theme",
          icon: theme === "light" ? Moon : Sun,
          run: () => setTheme(theme === "light" ? "dark" : "light"),
        },
        {
          key: "sidebar",
          label: sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar",
          icon: PanelLeft,
          keys: ["["],
          run: toggleSidebar,
        },
        { key: "shortcuts", label: "Keyboard shortcuts", icon: Keyboard, keys: ["?"], run: openShortcuts },
      ] as const
    )
      .filter(({ label }) => matches(label))
      .map(({ key, label, icon, run, ...rest }): Entry => ({
        key: `action-${key}`,
        section: "Actions",
        label,
        icon,
        keys: "keys" in rest ? [...rest.keys] : undefined,
        run: () => {
          close();
          run();
        },
      })),
  ];
  // The list can shrink under the highlighted row (a filter, new results); keep the highlight on a real row.
  const current = Math.min(active, entries.length - 1);
  const optionId = (index: number) => `${listId}-option-${index}`;

  // Keep the highlighted row in view while moving with the arrow keys.
  useEffect(() => {
    if (open) document.getElementById(`${listId}-option-${current}`)?.scrollIntoView({ block: "nearest" });
  }, [open, current, listId]);

  const onInputKey = (event: React.KeyboardEvent) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive(current + 1 >= entries.length ? 0 : current + 1);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive(current <= 0 ? entries.length - 1 : current - 1);
    } else if (event.key === "Home") {
      event.preventDefault();
      setActive(0);
    } else if (event.key === "End") {
      event.preventDefault();
      setActive(entries.length - 1);
    } else if (event.key === "Enter" && entries[current]) {
      event.preventDefault();
      entries[current].run();
    }
  };

  const recordsPending = searching && (hits.isFetching || term !== q);

  return (
    <Dialog.Root open={open} onOpenChange={(value) => (value ? show() : close())}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-scrim data-[state=closed]:animate-[fade-out_var(--duration-hover)_var(--ease-exit)] data-[state=open]:animate-[fade-in_var(--duration-hover)_ease-out]" />
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed top-[10vh] left-1/2 z-50 flex w-[min(94vw,640px)] -translate-x-1/2 flex-col overflow-hidden rounded-xl border border-line bg-surface text-ink shadow-[0_24px_80px_-20px_rgba(0,0,0,.4)] data-[state=closed]:animate-[palette-out_var(--duration-hover)_var(--ease-exit)] data-[state=open]:animate-[palette-in_var(--duration-menu)_var(--ease-enter)] sm:top-[16vh]"
        >
          <Dialog.Title className="sr-only">Search and commands</Dialog.Title>
          <div className="flex items-center gap-3 border-b border-line px-4">
            <Search className="size-4 shrink-0 text-ink-3" aria-hidden />
            <input
              autoFocus
              role="combobox"
              aria-expanded
              aria-controls={listId}
              aria-activedescendant={entries.length ? optionId(current) : undefined}
              aria-label="Search records, pages and actions"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setActive(0);
              }}
              onKeyDown={onInputKey}
              placeholder="Search records, or jump to a page or action…"
              className="h-12 min-w-0 flex-1 bg-transparent text-[14px] outline-none placeholder:text-ink-3"
            />
            <kbd className="kbd">Esc</kbd>
          </div>

          {searching && (
            <div
              className="flex gap-1 border-b border-line px-3 py-2"
              role="group"
              aria-label="Limit records to one type"
            >
              {RECORD_FILTERS.map((name) => (
                <button
                  key={name}
                  onClick={() => {
                    setFilter(name);
                    setActive(0);
                  }}
                  aria-pressed={filter === name}
                  className={cn(
                    "rounded-md px-2 py-1 text-[11.5px] font-medium transition-colors duration-[var(--duration-hover)]",
                    filter === name
                      ? "bg-brand-soft text-brand-ink"
                      : "text-ink-3 hover:bg-surface-muted hover:text-ink-2",
                  )}
                >
                  {name === "All" ? "All records" : `${name}s`.replace("Companys", "Companies")}
                </button>
              ))}
            </div>
          )}

          <div
            id={listId}
            role="listbox"
            aria-label="Results"
            className="max-h-[min(56vh,420px)] overflow-y-auto p-1.5"
          >
            {hits.isError && searching && (
              <p className="px-2.5 py-2 text-[12px] text-down" role="alert">
                Record search is unavailable right now. Pages and actions still work.
              </p>
            )}
            {entries.length === 0 && !recordsPending && (
              <div className="px-5 py-9 text-center">
                <p className="text-[13.5px] font-medium">Nothing matches “{query.trim()}”</p>
                <p className="mt-1 text-[12px] text-ink-3">Check the spelling, or create it as a new record.</p>
                <div className="mt-4 flex justify-center gap-2">
                  {(["lead", "contact", "company"] as const).map((kind) => (
                    <button
                      key={kind}
                      onClick={() => {
                        close();
                        openCreate(kind);
                      }}
                      className="inline-flex h-8 items-center gap-1.5 rounded-md border border-line px-2.5 text-[12.5px] font-medium text-ink-2 hover:border-line-strong hover:text-ink"
                    >
                      <Plus className="size-3.5" aria-hidden />
                      New {kind}
                    </button>
                  ))}
                </div>
              </div>
            )}
            {SECTIONS.map((section) => {
              const rows = entries
                .map((entry, index) => ({ entry, index }))
                .filter(({ entry }) => entry.section === section);
              const showPending = section === "Records" && recordsPending;
              if (rows.length === 0 && !showPending) return null;
              return (
                <div key={section} role="group" aria-label={section} className="pb-1">
                  <p className="px-2.5 pt-2 pb-1 text-[11px] font-medium text-ink-3" aria-hidden>
                    {section}
                    {showPending && <span className="font-normal"> · searching…</span>}
                  </p>
                  {rows.map(({ entry, index }) => (
                    <div
                      key={entry.key}
                      id={optionId(index)}
                      role="option"
                      aria-selected={index === current}
                      onPointerMove={() => index !== current && setActive(index)}
                      onClick={entry.run}
                      className={cn(
                        "flex h-9 cursor-pointer items-center gap-2.5 rounded-md px-2.5",
                        index === current && "bg-surface-muted",
                      )}
                    >
                      <entry.icon
                        className={cn("size-4 shrink-0", index === current ? "text-ink" : "text-ink-3")}
                        aria-hidden
                      />
                      <span className="min-w-0 truncate text-[13px]">
                        <Highlight text={entry.label} query={q} />
                      </span>
                      {entry.detail && (
                        <span className="min-w-0 shrink truncate text-[12px] text-ink-3">{entry.detail}</span>
                      )}
                      <span className="ml-auto flex shrink-0 items-center gap-0.5" aria-hidden>
                        {index === current ? (
                          <CornerDownLeft className="size-3.5 text-ink-3" />
                        ) : (
                          entry.keys?.map((key) => (
                            <kbd key={key} className="kbd">
                              {key}
                            </kbd>
                          ))
                        )}
                      </span>
                    </div>
                  ))}
                </div>
              );
            })}
          </div>

          <div
            className="flex items-center gap-4 border-t border-line bg-canvas/60 px-4 py-2 text-[11px] text-ink-3"
            aria-hidden
          >
            <span className="flex items-center gap-1">
              <kbd className="kbd">↑</kbd>
              <kbd className="kbd">↓</kbd> Move
            </span>
            <span className="flex items-center gap-1">
              <kbd className="kbd">↵</kbd> Open
            </span>
            <span className="ml-auto flex items-center gap-1">
              <kbd className="kbd">?</kbd> All shortcuts
            </span>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
