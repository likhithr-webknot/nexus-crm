import { activityCounts, listActivities, type Activity, type ActivityType } from "@/lib/activities";
import { localIso } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";

export type FeedItem = Activity & { local: string };

/** The timeline, newest first, loaded 50 at a time, and how many of each type there are. */
export function useActivities(type: ActivityType | "", actor: string) {
  const counts = useQuery({
    queryKey: [...KEYS.activities, "counts", actor],
    queryFn: () => activityCounts(actor || undefined),
  });
  const feed = useInfiniteQuery({
    queryKey: [...KEYS.activities, "feed", type, actor],
    queryFn: ({ pageParam }) => listActivities({ type, actorId: actor || undefined, page: pageParam, size: 50 }),
    initialPageParam: 1,
    getNextPageParam: (last) => (last.page * last.size < last.total ? last.page + 1 : undefined),
  });

  const items: FeedItem[] = (feed.data?.pages.flatMap((p) => p.items) ?? []).map((a) => ({
    ...a,
    local: localIso(a.occurredAt),
  }));
  // Activities arrive newest first, so each day's entries are already together.
  const days = [...new Set(items.map((a) => a.local.slice(0, 10)))];
  const countFor = (t: ActivityType | "") =>
    t ? (counts.data?.[t] ?? 0) : Object.values(counts.data ?? {}).reduce((sum, n) => sum + (n ?? 0), 0);

  return { feed, items, days, countFor };
}
