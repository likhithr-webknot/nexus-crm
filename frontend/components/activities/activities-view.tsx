"use client";

import { useSession } from "@/components/auth/session";
import { DeleteDialog } from "@/components/ui/delete-dialog";
import { LoadFailed, LoadingNote } from "@/components/ui/load-state";
import { Card, EmptyState, Page, PageHeader, buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { useAssignableUsers } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import type { ActivityType } from "@/lib/activities";
import { deleteActivity, type Activity } from "@/lib/activities";
import { AFTER } from "@/lib/query-keys";
import { Inbox, Plus } from "lucide-react";
import { useState } from "react";
import { ActivityFeed } from "./activity-feed";
import { ActivityFilters } from "./activity-filters";
import { EditActivityDialog } from "./edit-activity-dialog";
import { LogActivityDialog } from "./log-activity-dialog";
import { useActivities } from "./use-activities";

export function ActivitiesView() {
  const [adding, setAdding] = useState(false);
  const [type, setType] = useState<ActivityType | "">("");
  const [actor, setActor] = useState("");
  const [editing, setEditing] = useState<Activity | null>(null);
  const [deleting, setDeleting] = useState<Activity | null>(null);
  const me = useSession();
  const refresh = useRefresh();

  const people = useAssignableUsers();
  const { feed, items, days, countFor } = useActivities(type, actor);
  const clear = () => {
    setType("");
    setActor("");
  };

  return (
    <Page>
      <PageHeader title="Activities" subtitle="Every call, email, meeting and change across your team.">
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          Log activity
        </button>
      </PageHeader>

      <Card>
        <ActivityFilters
          type={type}
          actor={actor}
          people={people.data ?? []}
          countFor={countFor}
          onType={setType}
          onActor={setActor}
        />

        {feed.isError && !feed.data ? (
          <div className="border-t border-line">
            <LoadFailed what="activity" onRetry={() => feed.refetch()} />
          </div>
        ) : feed.isPending ? (
          <LoadingNote what="activity" />
        ) : items.length === 0 ? (
          <div className="border-t border-line">
            <EmptyState
              icon={Inbox}
              title="No activity matches"
              body="Nothing has been logged for this combination of filters."
            >
              <button onClick={clear} className={buttonSecondary}>
                Clear filters
              </button>
            </EmptyState>
          </div>
        ) : (
          <ActivityFeed
            days={days}
            items={items}
            // Only the author can change an entry, and automatic entries are never changed by hand.
            canChange={(a) => a.type !== "SYSTEM" && a.actorId === me.id}
            onEdit={setEditing}
            onDelete={setDeleting}
          />
        )}

        {feed.hasNextPage && (
          <div className="border-t border-line p-3 text-center">
            <button onClick={() => feed.fetchNextPage()} disabled={feed.isFetchingNextPage} className={buttonSecondary}>
              {feed.isFetchingNextPage ? "Loading…" : "Load older activity"}
            </button>
          </div>
        )}
      </Card>

      <EditActivityDialog activity={editing} onClose={() => setEditing(null)} />
      <DeleteDialog
        target={deleting}
        title={(a) => `Delete "${a.subject}"?`}
        body="The entry is removed from the timeline."
        successMessage={() => "Activity deleted."}
        action={(a) => deleteActivity(a.id)}
        onClose={() => setDeleting(null)}
        onDeleted={() => void refresh(AFTER.activity)}
      />
      <LogActivityDialog open={adding} onClose={() => setAdding(false)} onLogged={clear} />
    </Page>
  );
}
