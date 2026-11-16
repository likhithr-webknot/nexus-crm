import { downloadCsv } from "@/lib/csv";
import type { Lead } from "@/lib/leads";
import { moneyValue } from "@/lib/money";
import { sourceLabel, stageLabel } from "@/lib/stages";

const HEADER = ["Lead name", "Company", "Email", "Phone", "Source", "Assigned to", "Stage", "Estimated value", "Currency", "Created"];

/** Saves the leads as a CSV file. */
export function exportLeads(leads: Lead[]) {
  downloadCsv(
    "leads.csv",
    HEADER,
    leads.map((l) => [
      l.name,
      l.companyName ?? "",
      l.email ?? "",
      l.phone ?? "",
      sourceLabel(l.source),
      l.owner?.name ?? "",
      stageLabel(l.status),
      l.estimatedValue ? moneyValue(l.estimatedValue) : "",
      l.estimatedValue?.currency ?? "",
      l.createdAt.slice(0, 10),
    ]),
  );
}
