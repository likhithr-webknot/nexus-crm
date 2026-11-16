import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { addDays, type Cell } from "@/lib/calendar";
import { followUpSummary, listFollowUps, setFollowUpDone } from "@/lib/follow-ups";
import { toInstant } from "@/lib/format";
import { AFTER, KEYS } from "@/lib/query-keys";
import { useMutation, useQuery } from "@tanstack/react-query";
import { byTime, withLocal, type Item } from "./follow-up-item";

/** Everything the follow-ups page reads: the calendar's days, the overdue list, and the counts. */
export function useFollowUps(grid: Cell[], today: string) {
  // The calendar loads every day it shows, including the greyed days around the month.
  const from = toInstant(grid[0].iso);
  const to = toInstant(addDays(grid[grid.length - 1].iso, 1));
  const calendar = useQuery({ queryKey: [...KEYS.followUps, "calendar", from, to], queryFn: () => listFollowUps({ from, to, size: 500 }) });
  const overdue = useQuery({ queryKey: [...KEYS.followUps, "overdue", today], queryFn: () => listFollowUps({ status: "OPEN", to: toInstant(today), size: 200 }) });
  const summary = useQuery({
    queryKey: [...KEYS.followUps, "summary", today],
    queryFn: () => followUpSummary(toInstant(today), toInstant(addDays(today, 1)), toInstant(`${today.slice(0, 7)}-01`)),
  });

  return {
    calendar,
    summary: summary.data,
    items: (calendar.data?.items ?? []).map(withLocal).sort(byTime),
    overdue: (overdue.data?.items ?? []).map(withLocal).sort(byTime),
  };
}

/** Ticks a follow-up done, or puts it back. */
export function useToggleFollowUp() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (item: Item) => setFollowUpDone(item.id, !item.done),
    onSuccess: () => refresh(AFTER.followUp),
    onError: (e) => toast(errorMessage(e, "Couldn't update the follow-up.")),
  });
}
