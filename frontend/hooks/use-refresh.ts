import { useQueryClient } from "@tanstack/react-query";

/** Returns a function that marks the given query groups stale, so screens reload them. */
export function useRefresh() {
  const queryClient = useQueryClient();
  return (keys: readonly (readonly string[])[]) =>
    Promise.all(keys.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
}
