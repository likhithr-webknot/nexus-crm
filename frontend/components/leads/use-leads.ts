import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { deleteLeads, leadSummary, listLeads, type LeadFilters } from "@/lib/leads";
import { AFTER, KEYS } from "@/lib/query-keys";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";

/** One page of leads for the given filters. The previous page stays on screen while the next one loads. */
export function useLeadList(filters: LeadFilters, page: number, size: number) {
  return useQuery({
    queryKey: [...KEYS.leads, filters, page],
    queryFn: () => listLeads({ ...filters, page, size }),
    placeholderData: keepPreviousData,
  });
}

export function useLeadSummary() {
  return useQuery({ queryKey: KEYS.leadSummary, queryFn: leadSummary });
}

export function useDeleteLeads(onDone: () => void) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (ids: string[]) => deleteLeads(ids),
    onSuccess: (_, ids) => {
      toast(`${ids.length === 1 ? "1 lead" : `${ids.length} leads`} deleted.`);
      void refresh(AFTER.lead);
      onDone();
    },
  });
}
