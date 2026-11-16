import { LeadsView } from "@/components/leads/leads-view";
import { LEADS } from "@/lib/mock-data";

export const metadata = { title: "Leads" };

export default function LeadsPage() {
  return <LeadsView leads={LEADS} />;
}
