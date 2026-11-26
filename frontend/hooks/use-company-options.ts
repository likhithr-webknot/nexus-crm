import { listCompanies } from "@/lib/contacts";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";

/** Companies to choose from in a picker (the API's largest page). Shares its cache with the other company lists. */
export function useCompanyOptions(enabled = true) {
  return useQuery({
    queryKey: [...KEYS.companies, "options"],
    queryFn: () => listCompanies({ size: 100 }),
    enabled,
  });
}
