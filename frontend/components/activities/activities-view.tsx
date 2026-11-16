"use client";

import { LoadFailed, LoadingNote } from "@/components/ui/load-state";
import { Card, EmptyState, Page, PageHeader, buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { useAssignableUsers } from "@/hooks/use-people";
import type { ActivityType } from "@/lib/activities";
import { Inbox, Plus } from "lucide-react";
import { useState } from "react";
import { ActivityFeed } from "./activity-feed";
import { ActivityFilters } from "./activity-filters";
import { LogActivityDialog } from "./log-activity-dialog";
import { useActivities } from "./use-activities";

export function ActivitiesView() {
  const [adding, setAdding] = useState(false);
  const [type, setType] = useState<ActivityType | "">("");
  const [actor, setActor] = useState("");

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
        <ActivityFilters type={type} actor={actor} people={people.data ?? []} countFor={countFor} onType={setType} onActor={setActor} />

        {feed.isError && !feed.data ? (
          <div className="border-t border-line">
            <LoadFailed what="activity" onRetry={() => feed.refetch()} />
          </div>
        ) : feed.isPending ? (
          <LoadingNote what="activity" />
        ) : items.length === 0 ? (
          <div className="border-t border-line">
            <EmptyState icon={Inbox} title="No activity matches" body="Nothing has been logged for this combination of filters.">
              <button onClick={clear} className={buttonSecondary}>
                Clear filters
              </button>
            </EmptyState>
          </div>
        ) : (
          <ActivityFeed days={days} items={items} />
        )}

        {feed.hasNextPage && (
          <div className="border-t border-line p-3 text-center">
            <button onClick={() => feed.fetchNextPage()} disabled={feed.isFetchingNextPage} className={buttonSecondary}>
              {feed.isFetchingNextPage ? "Loading…" : "Load older activity"}
            </button>
          </div>
        )}
      </Card>

      <LogActivityDialog open={adding} onClose={() => setAdding(false)} onLogged={clear} />
    </Page>
  );
}
