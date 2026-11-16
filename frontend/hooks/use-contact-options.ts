import { listContacts } from "@/lib/contacts";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";

/** Contacts to choose from in a picker. */
export function useContactOptions(enabled = true) {
  return useQuery({ queryKey: [...KEYS.contacts, ""], queryFn: () => listContacts({ size: 100 }), enabled });
}
