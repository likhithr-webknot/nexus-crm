"use client";

import { useState } from "react";
import { Search, SearchX, UserPlus } from "lucide-react";
import type { Role, TeamMember } from "@/lib/mock-data";
import {
  Avatar,
  Badge,
  Card,
  EmptyState,
  Meter,
  NEEDS_API,
  Page,
  PageHeader,
  buttonPrimary,
  buttonSecondary,
  control,
} from "@/components/ui/primitives";
import { cn, formatDate } from "@/lib/format";

const ROLE_TONE: Record<Role, "violet" | "brand" | "neutral"> = { Admin: "violet", Manager: "brand", Rep: "neutral" };
const STATUS_DOT: Record<TeamMember["status"], string> = {
  Active: "bg-emerald-500",
  Invited: "bg-amber-500",
  Disabled: "bg-slate-300",
};

export function TeamView({ members }: { members: TeamMember[] }) {
  const [query, setQuery] = useState("");
  const [role, setRole] = useState<Role | "">("");

  const q = query.trim().toLowerCase();
  const shown = members.filter(
    (m) => (!role || m.role === role) && (!q || m.name.toLowerCase().includes(q) || m.email.toLowerCase().includes(q)),
  );
  const count = (status: TeamMember["status"]) => members.filter((m) => m.status === status).length;

  return (
    <Page>
      <PageHeader
        title="Team Management"
        subtitle={`${count("Active")} active, ${count("Invited")} invited, ${count("Disabled")} disabled. Access is invite-only.`}
      >
        <button className={buttonPrimary} disabled title={NEEDS_API}>
          <UserPlus className="size-4" aria-hidden />
          Invite member
        </button>
      </PageHeader>

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 p-4">
          <label className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <span className="sr-only">Search team</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search name or email…"
              className={cn(control, "w-full pr-3 pl-9 placeholder:text-ink-3")}
            />
          </label>
          <select
            aria-label="Role"
            value={role}
            onChange={(e) => setRole(e.target.value as Role | "")}
            className={cn(control, "px-2.5 sm:ml-auto")}
          >
            <option value="">All roles</option>
            <option>Admin</option>
            <option>Manager</option>
            <option>Rep</option>
          </select>
        </div>

        {shown.length === 0 ? (
          <div className="border-t border-line">
            <EmptyState icon={SearchX} title="No team members found" body="Nobody matches this search and role.">
              <button
                onClick={() => {
                  setQuery("");
                  setRole("");
                }}
                className={buttonSecondary}
              >
                Clear filters
              </button>
            </EmptyState>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-[13px]">
              <thead>
                <tr className="border-y border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
                  <th className="py-2.5 pr-3 pl-5 font-medium">Member</th>
                  <th className="px-3 py-2.5 font-medium">Role</th>
                  <th className="px-3 py-2.5 font-medium">Status</th>
                  <th className="px-3 py-2.5 text-right font-medium">Leads</th>
                  <th className="px-3 py-2.5 text-right font-medium">Deals won</th>
                  <th className="w-48 px-3 py-2.5 pl-6 font-medium">Quota attainment</th>
                  <th className="py-2.5 pr-5 pl-3 text-right font-medium">Last active</th>
                </tr>
              </thead>
              <tbody className="tnum divide-y divide-line">
                {shown.map((m) => {
                  // People who have not sold yet have no numbers to show.
                  const selling = m.status === "Active";
                  return (
                    <tr key={m.id} className={cn("hover:bg-canvas/60", m.status === "Disabled" && "text-ink-3")}>
                      <td className="py-3 pr-3 pl-5">
                        <div className="flex items-center gap-3">
                          <Avatar name={m.name} size="md" />
                          <div className="min-w-0">
                            <p className={cn("font-medium", m.status === "Disabled" ? "text-ink-3" : "text-ink")}>{m.name}</p>
                            <p className="truncate text-[12.5px] text-ink-3">{m.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        <Badge tone={ROLE_TONE[m.role]}>{m.role}</Badge>
                      </td>
                      <td className="px-3 py-3">
                        <span className="inline-flex items-center gap-2 text-ink-2">
                          <span className={cn("size-2 rounded-full", STATUS_DOT[m.status])} aria-hidden />
                          {m.status}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-right text-ink-2">{selling ? m.leads : "–"}</td>
                      <td className="px-3 py-3 text-right text-ink-2">{selling ? m.dealsWon : "–"}</td>
                      <td className="px-3 py-3 pl-6">
                        {selling ? (
                          <div className="flex items-center gap-3">
                            <Meter value={m.attainment} className={m.attainment >= 80 ? "bg-up" : undefined} />
                            <span className="w-9 text-right font-medium text-ink">{m.attainment}%</span>
                          </div>
                        ) : (
                          <span className="text-ink-3">–</span>
                        )}
                      </td>
                      <td className="py-3 pr-5 pl-3 text-right whitespace-nowrap text-ink-2">
                        {m.lastActive ? formatDate(m.lastActive) : "Never signed in"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Page>
  );
}
