"use client";

import { UserAvatar } from "@/components/auth/session";
import { Badge, buttonSecondary } from "@/components/ui/primitives";
import { ROLE_LABELS } from "@/lib/auth";
import { cn, formatDate } from "@/lib/format";
import type { User } from "@/lib/users";
import { ROLE_TONE, STATUS } from "./team-constants";

export function MemberTable({ members, meId, isAdmin, onManage }: { members: User[]; meId: string; isAdmin: boolean; onManage: (user: User) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-[13px]">
        <thead>
          <tr className="border-b border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
            <th className="py-2.5 pr-3 pl-5 font-medium">Member</th>
            <th className="px-3 py-2.5 font-medium">Role</th>
            <th className="px-3 py-2.5 font-medium">Status</th>
            <th className="px-3 py-2.5 font-medium">Last sign-in</th>
            {isAdmin && <th className="py-2.5 pr-5 pl-3 text-right font-medium">Actions</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {members.map((u) => (
            <tr key={u.id} className="hover:bg-canvas/60">
              <td className="py-3 pr-3 pl-5">
                <div className="flex items-center gap-3">
                  <UserAvatar user={u} />
                  <div className="min-w-0">
                    <p className={cn("flex items-center gap-2 font-medium", u.status === "DISABLED" ? "text-ink-3" : "text-ink")}>
                      {u.displayName}
                      {u.id === meId && <Badge>You</Badge>}
                    </p>
                    <p className="truncate text-[12.5px] text-ink-3">{u.email}</p>
                  </div>
                </div>
              </td>
              <td className="px-3 py-3">
                <Badge tone={ROLE_TONE[u.role]}>{ROLE_LABELS[u.role]}</Badge>
              </td>
              <td className="px-3 py-3">
                <span className="inline-flex items-center gap-2 text-ink-2">
                  <span className={cn("size-2 rounded-full", STATUS[u.status].dot)} aria-hidden />
                  {STATUS[u.status].label}
                </span>
              </td>
              <td className="tnum px-3 py-3 whitespace-nowrap text-ink-2">
                {u.lastLoginAt ? formatDate(u.lastLoginAt) : "Never signed in"}
              </td>
              {isAdmin && (
                <td className="py-3 pr-5 pl-3 text-right">
                  {/* The backend refuses changes to your own account, so there is always an admin left. */}
                  {u.id !== meId && (
                    <button onClick={() => onManage(u)} className={cn(buttonSecondary, "h-8")}>
                      Manage
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Placeholder rows while the team loads. */
export function MemberTableSkeleton() {
  return (
    <ul aria-label="Loading team" aria-busy className="divide-y divide-line">
      {[0, 1, 2].map((i) => (
        <li key={i} className="flex animate-pulse items-center gap-3 px-5 py-3.5 motion-reduce:animate-none">
          <span className="size-8 rounded-full bg-canvas" />
          <span className="h-3 w-40 rounded bg-canvas" />
          <span className="ml-auto h-3 w-24 rounded bg-canvas" />
        </li>
      ))}
    </ul>
  );
}
