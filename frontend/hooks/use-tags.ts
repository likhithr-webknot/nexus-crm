import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { AFTER, KEYS } from "@/lib/query-keys";
import { createTag, listTags, tagRecords, type TagColor, type TagKind } from "@/lib/tags";
import { useMutation, useQuery } from "@tanstack/react-query";

/** Every tag, for pickers and filters. */
export function useTags() {
  return useQuery({ queryKey: KEYS.tags, queryFn: listTags, staleTime: 5 * 60_000 });
}

export function useCreateTag() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (v: { name: string; color?: TagColor }) => createTag(v.name, v.color),
    onSuccess: () => void refresh([KEYS.tags]),
  });
}

/** Adds or removes tags on the given records, then refreshes every list that shows tags. */
export function useChangeTags(kind: TagKind, onDone?: () => void) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (v: { ids: string[]; add?: string[]; remove?: string[] }) => tagRecords(kind, v),
    onSuccess: (r, v) => {
      toast(`${v.add?.length ? "Tag added to" : "Tag removed from"} ${r.count === 1 ? "1 record" : `${r.count} records`}.`);
      void refresh(AFTER.tag);
      onDone?.();
    },
  });
}
