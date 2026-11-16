"use client";

import { useDebounced } from "@/hooks/use-debounced";
import { search } from "@/lib/notifications";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";

/** The top-bar search. Results appear below it as you type, and Ctrl or ⌘ + K jumps to it. */
export function GlobalSearch() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const term = useDebounced(query.trim(), 250);
  const hits = useQuery({ queryKey: ["search", term], queryFn: () => search(term), enabled: term.length >= 2, staleTime: 30_000 });

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="relative hidden md:block">
      <label>
        <span className="sr-only">Search</span>
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
        <input
          ref={inputRef}
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === "Escape" && setQuery("")}
          placeholder="Search leads, contacts, deals…"
          className="h-9 w-64 rounded-lg border border-line bg-canvas pr-12 pl-9 text-[13px] text-ink placeholder:text-ink-3 focus:border-brand focus:bg-surface focus:outline-none lg:w-80"
        />
        <kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded border border-line bg-surface px-1.5 py-0.5 font-mono text-[10px] text-ink-3">⌘K</kbd>
      </label>
      {query.trim().length >= 2 && (
        <div className="absolute top-11 left-0 z-40 max-h-96 w-full min-w-80 overflow-y-auto rounded-xl border border-line bg-surface py-1.5 shadow-lg" role="listbox" aria-label="Search results">
          {hits.isPending ? (
            <p className="px-4 py-3 text-[13px] text-ink-3">Searching…</p>
          ) : hits.isError ? (
            <p className="px-4 py-3 text-[13px] text-down">Search isn&apos;t available right now.</p>
          ) : hits.data.length === 0 ? (
            <p className="px-4 py-3 text-[13px] text-ink-3">Nothing matches &ldquo;{term}&rdquo;.</p>
          ) : (
            hits.data.map((hit) => (
              <Link key={`${hit.type}-${hit.id}`} href={hit.href} onClick={() => setQuery("")} role="option" aria-selected={false} className="flex items-baseline gap-3 px-4 py-2 hover:bg-canvas">
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
  );
}
