import { useSession } from "@/components/auth/session";
import { KEYS } from "@/lib/query-keys";
import { listAssignable } from "@/lib/users";
import { useQuery } from "@tanstack/react-query";

/** The people the signed-in user can give a record to. Reps get only themselves. */
export function useAssignableUsers(enabled = true) {
  return useQuery({ queryKey: KEYS.assignable, queryFn: listAssignable, enabled });
}

/** The same people as picker options. Until the list loads, the signed-in user is offered so the picker is never empty. */
export function useOwnerOptions(enabled = true) {
  const session = useSession();
  const people = useAssignableUsers(enabled);
  return people.data ?? [{ id: session.id, displayName: session.displayName }];
}
