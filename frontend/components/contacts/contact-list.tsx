import { SavedViewsMenu } from "@/components/saved-views/saved-views-menu";
import { TagList } from "@/components/tags/tag-chip";
import { LoadFailed, LoadingNote } from "@/components/ui/load-state";
import { Avatar, Card, EmptyState, buttonSecondary, control } from "@/components/ui/primitives";
import type { Contact } from "@/lib/contacts";
import { cn } from "@/lib/format";
import type { PageResponse } from "@/lib/page";
import type { Tag } from "@/lib/tags";
import { Search, SearchX } from "lucide-react";

/** The searchable list of contacts. It shows what it is given and reports what the user picks or types. */
export function ContactList({
  query,
  onQuery,
  tag,
  onTag,
  tags,
  ticked,
  onTick,
  bulk,
  result,
  status,
  selectedId,
  onSelect,
  onRetry,
}: {
  query: string;
  onQuery: (query: string) => void;
  tag: string;
  onTag: (tag: string) => void;
  tags: Tag[];
  ticked: ReadonlySet<string>;
  onTick: (id: string, on: boolean) => void;
  /** The bulk action bar, shown while something is ticked. */
  bulk: React.ReactNode;
  result: PageResponse<Contact> | undefined;
  status: "pending" | "error" | "success";
  selectedId: string | undefined;
  onSelect: (id: string) => void;
  onRetry: () => void;
}) {
  const contacts = result?.items ?? [];
  const searching = query.trim() !== "";
  const count = result
    ? result.total > contacts.length
      ? `First ${contacts.length} of ${result.total}`
      : `${result.total} contacts`
    : "";

  return (
    <Card>
      <div className="flex items-center gap-3 p-4">
        <label className="relative flex-1 sm:max-w-xs">
          <span className="sr-only">Search contacts</span>
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3"
            aria-hidden
          />
          <input
            type="search"
            value={query}
            onChange={(e) => onQuery(e.target.value)}
            placeholder="Search name, company, email…"
            className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
          />
        </label>
        {tags.length > 0 && (
          <select
            aria-label="Tag"
            value={tag}
            onChange={(e) => onTag(e.target.value)}
            className={cn(control, "px-2.5")}
          >
            <option value="">All tags</option>
            {tags.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        )}
        <SavedViewsMenu
          kind="contact"
          current={{ q: query.trim(), tag }}
          onApply={(f) => {
            onQuery(f.q ?? "");
            onTag(f.tag ?? "");
          }}
        />
        <p className="tnum ml-auto text-[12.5px] text-ink-3" aria-live="polite">
          {count}
        </p>
      </div>
      {bulk}

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
            body={
              searching
                ? "Check the spelling, or search by company or email instead."
                : "Add a contact, or convert a lead to create one."
            }
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
            <li key={c.id} className="flex items-center">
              <label className="grid shrink-0 cursor-pointer place-items-center py-3 pr-1 pl-4">
                <span className="sr-only">Select {c.name}</span>
                <input
                  type="checkbox"
                  checked={ticked.has(c.id)}
                  onChange={(e) => onTick(c.id, e.target.checked)}
                  className="size-4 accent-brand"
                />
              </label>
              <button
                onClick={() => onSelect(c.id)}
                aria-current={c.id === selectedId ? "true" : undefined}
                className={cn(
                  "flex min-w-0 flex-1 items-center gap-3 px-3 py-3 text-left transition-colors",
                  c.id === selectedId ? "bg-brand-soft/60" : "hover:bg-surface-muted",
                )}
              >
                <Avatar name={c.name} size="md" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13.5px] font-medium text-ink">{c.name}</span>
                  <span className="block truncate text-[12.5px] text-ink-3">
                    {[c.jobTitle, c.company?.name].filter(Boolean).join(" · ") || "—"}
                  </span>
                  {(c.tags ?? []).length > 0 && (
                    <span className="mt-1 block">
                      <TagList tags={c.tags ?? []} />
                    </span>
                  )}
                </span>
                <span className="hidden w-64 shrink-0 truncate text-[12.5px] text-ink-2 md:block">
                  {c.email ?? "—"}
                </span>
                <span className="tnum hidden w-40 shrink-0 truncate text-[12.5px] text-ink-2 lg:block">
                  {c.phone ?? "—"}
                </span>
                <span className="hidden w-40 shrink-0 truncate text-[12.5px] text-ink-3 xl:block">{c.owner.name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
