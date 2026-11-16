"use client";

import { useSession } from "@/components/auth/session";
import { LoadFailed } from "@/components/ui/load-state";
import { Card, EmptyState, Page, PageHeader, buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import type { Role } from "@/lib/auth";
import { KEYS } from "@/lib/query-keys";
import { listUsers, type User, type UserStatus } from "@/lib/users";
import { useQuery } from "@tanstack/react-query";
import { SearchX, UserPlus } from "lucide-react";
import { useState } from "react";
import { InviteDialog } from "./invite-dialog";
import { ManageDialog } from "./manage-member-dialog";
import { MemberFilters } from "./member-filters";
import { MemberTable, MemberTableSkeleton } from "./member-table";
import { TeamsCard } from "./teams-card";

export function TeamView() {
  const me = useSession();
  const isAdmin = me.role === "ADMIN";
  const users = useQuery({ queryKey: KEYS.users, queryFn: listUsers });
  const [query, setQuery] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [inviting, setInviting] = useState(false);
  const [managing, setManaging] = useState<User | null>(null);

  const q = query.trim().toLowerCase();
  const all = users.data ?? [];
  const shown = all.filter((u) => (!role || u.role === role) && (!q || u.displayName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)));
  const count = (status: UserStatus) => all.filter((u) => u.status === status).length;

  return (
    <Page>
      <PageHeader
        title="Team Management"
        subtitle={
          users.data
            ? `${count("ACTIVE")} active, ${count("INVITED")} invited, ${count("DISABLED")} disabled. ${isAdmin ? "Access is invite-only." : "Only admins can invite or change members."}`
            : "Who has access to Nexus, and what they can do."
        }
      >
        {isAdmin && (
          <button className={buttonPrimary} onClick={() => setInviting(true)}>
            <UserPlus className="size-4" aria-hidden />
            Invite member
          </button>
        )}
      </PageHeader>

      <Card className="overflow-hidden">
        <MemberFilters query={query} role={role} onQuery={setQuery} onRole={setRole} />
        <div className="border-t border-line">
          {!users.data && users.error ? (
            <LoadFailed what="the team" onRetry={() => users.refetch()} />
          ) : !users.data ? (
            <MemberTableSkeleton />
          ) : shown.length === 0 ? (
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
          ) : (
            <MemberTable members={shown} meId={me.id} isAdmin={isAdmin} onManage={setManaging} />
          )}
        </div>
      </Card>

      <TeamsCard isAdmin={isAdmin} />

      <InviteDialog open={inviting} onClose={() => setInviting(false)} />
      <ManageDialog user={managing} onClose={() => setManaging(null)} />
    </Page>
  );
}
