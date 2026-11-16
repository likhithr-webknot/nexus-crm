import { LoadFailed, LoadingNote } from "@/components/ui/load-state";
import { Avatar, Card, EmptyState, buttonSecondary, control } from "@/components/ui/primitives";
import type { Contact } from "@/lib/contacts";
import { cn } from "@/lib/format";
import type { PageResponse } from "@/lib/page";
import { Search, SearchX } from "lucide-react";

/** The searchable list of contacts. It shows what it is given and reports what the user picks or types. */
export function ContactList({
  query,
  onQuery,
  result,
  status,
  selectedId,
  onSelect,
  onRetry,
}: {
  query: string;
  onQuery: (query: string) => void;
  result: PageResponse<Contact> | undefined;
  status: "pending" | "error" | "success";
  selectedId: string | undefined;
  onSelect: (id: string) => void;
  onRetry: () => void;
}) {
  const contacts = result?.items ?? [];
  const searching = query.trim() !== "";
  const count = result ? (result.total > contacts.length ? `First ${contacts.length} of ${result.total}` : `${result.total} contacts`) : "";

  return (
    <Card>
      <div className="flex items-center gap-3 p-4">
        <label className="relative flex-1 sm:max-w-xs">
          <span className="sr-only">Search contacts</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
          <input
            type="search"
            value={query}
            onChange={(e) => onQuery(e.target.value)}
            placeholder="Search name, company, email…"
            className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
          />
        </label>
        <p className="tnum ml-auto text-[12.5px] text-ink-3" aria-live="polite">
          {count}
        </p>
      </div>

      {status === "error" && !result ? (
        <div className="border-t border-line">
          <LoadFailed what="contacts" onRetry={onRetry} />
        </div>
      ) : status === "pending" ? (
        <LoadingNote what="contacts" />
      ) : contacts.length === 0 ? (
        <div className="border-t border-line">
          <EmptyState
            icon={SearchX}
            title={searching ? "No contacts found" : "No contacts yet"}
            body={searching ? "Check the spelling, or search by company or email instead." : "Add a contact, or convert a lead to create one."}
          >
            {searching && (
              <button onClick={() => onQuery("")} className={buttonSecondary}>
                Clear search
              </button>
            )}
          </EmptyState>
        </div>
      ) : (
        <ul className="divide-y divide-line border-t border-line">
          {contacts.map((c) => (
            <li key={c.id}>
              <button
                onClick={() => onSelect(c.id)}
                aria-current={c.id === selectedId ? "true" : undefined}
                className={cn("flex w-full items-center gap-3 px-4 py-3 text-left transition-colors", c.id === selectedId ? "bg-brand-soft/60" : "hover:bg-canvas/70")}
              >
                <Avatar name={c.name} size="md" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13.5px] font-medium text-ink">{c.name}</span>
                  <span className="block truncate text-[12.5px] text-ink-3">{[c.jobTitle, c.company?.name].filter(Boolean).join(" · ") || "—"}</span>
                </span>
                <span className="hidden w-60 shrink-0 truncate text-[12.5px] text-ink-2 md:block">{c.email}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
