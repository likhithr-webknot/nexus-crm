"use client";

import { useDebounced } from "@/hooks/use-debounced";
import { search, type SearchHit } from "@/lib/notifications";
import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";

/** The top-bar search. Results appear as you type, and Ctrl or ⌘ + K jumps to it. On a phone it opens as a bar under the top bar. */
export function GlobalSearch() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const term = useDebounced(query.trim(), 250);
  const hits = useQuery({ queryKey: ["search", term], queryFn: () => search(term), enabled: term.length >= 2, staleTime: 30_000 });
  const done = () => {
    setQuery("");
    setOpen(false);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen(true);
        inputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const showResults = query.trim().length >= 2;
  const results = <SearchResults term={term} pending={hits.isPending} failed={hits.isError} hits={hits.data} onPick={done} />;

  return (
    <>
      {/* Phones: an icon that opens the search bar. */}
      <button aria-label="Search" onClick={() => setOpen((o) => !o)} className="grid size-9 place-items-center rounded-lg text-ink-2 hover:bg-canvas md:hidden">
        {open ? <X className="size-[18px]" /> : <Search className="size-[18px]" />}
      </button>

      <div className={open ? "fixed inset-x-0 top-16 z-40 border-b border-line bg-surface p-3 shadow-md md:relative md:inset-auto md:top-auto md:border-0 md:bg-transparent md:p-0 md:shadow-none" : "hidden md:relative md:block"}>
        <label className="relative block">
          <span className="sr-only">Search</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
          <input
            ref={inputRef}
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Escape" && done()}
            placeholder="Search leads, contacts, deals…"
            className="h-10 w-full rounded-lg border border-line bg-canvas pr-12 pl-9 text-[14px] text-ink placeholder:text-ink-3 focus:border-brand focus:bg-surface focus:outline-none md:h-9 md:w-64 md:text-[13px] lg:w-80"
          />
          <kbd className="pointer-events-none absolute top-1/2 right-2 hidden -translate-y-1/2 rounded border border-line bg-surface px-1.5 py-0.5 font-mono text-[10px] text-ink-3 md:block">⌘K</kbd>
        </label>
        {showResults && (
          <div className="mt-2 max-h-[60vh] w-full overflow-y-auto rounded-xl border border-line bg-surface py-1.5 shadow-lg md:absolute md:top-11 md:left-0 md:z-40 md:mt-0 md:max-h-96 md:min-w-80" role="listbox" aria-label="Search results">
            {results}
          </div>
        )}
      </div>
    </>
  );
}

function SearchResults({ term, pending, failed, hits, onPick }: { term: string; pending: boolean; failed: boolean; hits: SearchHit[] | undefined; onPick: () => void }) {
  if (pending) return <p className="px-4 py-3 text-[13px] text-ink-3">Searching…</p>;
  if (failed) return <p className="px-4 py-3 text-[13px] text-down">Search isn&apos;t available right now.</p>;
  if (!hits || hits.length === 0) return <p className="px-4 py-3 text-[13px] text-ink-3">Nothing matches &ldquo;{term}&rdquo;.</p>;
  return hits.map((hit) => (
    <Link key={`${hit.type}-${hit.id}`} href={hit.href} onClick={onPick} role="option" aria-selected={false} className="flex items-baseline gap-3 px-4 py-2.5 hover:bg-canvas">
      <span className="w-14 shrink-0 text-[11px] font-semibold tracking-wide text-ink-3 uppercase">{hit.type}</span>
      <span className="min-w-0">
        <span className="block truncate text-[13px] font-medium text-ink">{hit.title}</span>
        {hit.subtitle && <span className="block truncate text-[12px] text-ink-3">{hit.subtitle}</span>}
      </span>
    </Link>
  ));
}
