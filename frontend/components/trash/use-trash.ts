import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { AFTER, KEYS } from "@/lib/query-keys";
import { listTrash, restoreFromTrash, TRASH_LABELS, type TrashItem, type TrashType } from "@/lib/trash";
import { useMutation, useQuery } from "@tanstack/react-query";

export function useTrash(type: TrashType | "") {
  return useQuery({ queryKey: [...KEYS.trash, type], queryFn: () => listTrash(type || undefined) });
}

/** Restores a record. A refusal (a duplicate email, a deleted company) is shown as a message. */
export function useRestore() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (item: TrashItem) => restoreFromTrash(item.type, item.id),
    onSuccess: (_, item) => {
      toast(`${TRASH_LABELS[item.type]} "${item.name}" restored.`);
      void refresh(AFTER.restore);
    },
    onError: (e) => toast(errorMessage(e, "Couldn't restore it. Try again.")),
  });
}
