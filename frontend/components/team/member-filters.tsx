import { control } from "@/components/ui/primitives";
import { ROLE_LABELS, type Role } from "@/lib/auth";
import { cn } from "@/lib/format";
import { Search } from "lucide-react";
import { ROLES } from "./team-constants";

export function MemberFilters({
  query,
  role,
  onQuery,
  onRole,
}: {
  query: string;
  role: Role | "";
  onQuery: (query: string) => void;
  onRole: (role: Role | "") => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 p-4">
      <label className="relative min-w-[200px] flex-1 sm:max-w-xs">
        <span className="sr-only">Search team</span>
        <Search
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3"
          aria-hidden
        />
        <input
          type="search"
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          placeholder="Search name or email…"
          className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
        />
      </label>
      <select
        aria-label="Role"
        value={role}
        onChange={(e) => onRole(e.target.value as Role | "")}
        className={cn(control, "px-2.5 sm:ml-auto")}
      >
        <option value="">All roles</option>
        {ROLES.map((r) => (
          <option key={r} value={r}>
            {ROLE_LABELS[r]}
          </option>
        ))}
      </select>
    </div>
  );
}
