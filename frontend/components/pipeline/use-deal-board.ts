import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { listStages, moveDeal, pipelineBoard, type Deal } from "@/lib/deals";
import { AFTER, KEYS } from "@/lib/query-keys";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export function useStages() {
  return useQuery({ queryKey: KEYS.stages, queryFn: listStages, staleTime: Infinity });
}

/** The stages and deals on the board, and the one action you can take on a deal: move it. */
export function useDealBoard(onMoved: (message: string) => void) {
  const refresh = useRefresh();
  const client = useQueryClient();
  const boardKey = [...KEYS.pipeline, "board"] as const;
  const stagesQuery = useStages();
  const boardQuery = useQuery({ queryKey: boardKey, queryFn: () => pipelineBoard() });
  const stages = stagesQuery.data ?? [];
  const stageByCode = new Map(stages.map((s) => [s.code, s]));

  const move = useMutation({
    mutationFn: (v: { deal: Deal; stageCode: string; lostReason?: string }) =>
      moveDeal(v.deal.id, { stageCode: v.stageCode, version: v.deal.version, lostReason: v.lostReason }),
    onMutate: async (move) => {
      await client.cancelQueries({ queryKey: boardKey });
      const previous = client.getQueryData<Deal[]>(boardKey);
      client.setQueryData<Deal[]>(boardKey, (deals) => deals?.map((deal) => deal.id === move.deal.id ? { ...deal, stageCode: move.stageCode } : deal));
      return { previous };
    },
    onSuccess: (deal, v) => {
      onMoved(`${deal.company.name} moved to ${stageByCode.get(v.stageCode)?.name ?? v.stageCode}.`);
      void refresh(AFTER.deal);
    },
    onError: (e, _move, context) => {
      if (context?.previous) client.setQueryData(boardKey, context.previous);
      toast(errorMessage(e, "The deal could not be moved."));
      // Someone else may have changed the deal, so show what it looks like now.
      void refresh(AFTER.deal);
    },
  });

  return {
    stages,
    stageByCode,
    deals: boardQuery.data ?? [],
    failed: boardQuery.isError || stagesQuery.isError,
    retry: () => {
      void boardQuery.refetch();
      void stagesQuery.refetch();
    },
    move,
    refresh: () => refresh(AFTER.deal),
  };
}
