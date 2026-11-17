/* Lead statuses and deal stages. */

export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "DISQUALIFIED" | "CONVERTED";

export const LEAD_STATUSES: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "CONVERTED"];

export const STAGE_LABELS: Record<string, string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  DISQUALIFIED: "Disqualified",
  CONVERTED: "Converted",
  IN_PROGRESS: "In progress",
  WON: "Won",
  LOST: "Lost",
};

export const stageLabel = (code: string) => STAGE_LABELS[code] ?? code;

export type LeadSource = "WEBSITE" | "SOCIAL" | "REFERRAL" | "CAMPAIGN" | "MANUAL" | "IMPORT";

export const SOURCE_LABELS: Record<LeadSource, string> = {
  WEBSITE: "Website",
  SOCIAL: "Social Media",
  REFERRAL: "Referral",
  CAMPAIGN: "Campaign",
  MANUAL: "Manual Entry",
  IMPORT: "Import",
};

export const SOURCES = Object.keys(SOURCE_LABELS) as LeadSource[];

export const sourceLabel = (code: string) => SOURCE_LABELS[code as LeadSource] ?? code;

/** Colours for the lead-source chart. */
export const SOURCE_COLORS: Record<string, string> = {
  WEBSITE: "#4f46e5",
  SOCIAL: "#38bdf8",
  REFERRAL: "#a78bfa",
  CAMPAIGN: "#f59e0b",
  MANUAL: "#94a3b8",
  IMPORT: "#10b981",
};
