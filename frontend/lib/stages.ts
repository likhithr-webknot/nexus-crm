/*
 * Lead statuses and deal stages. A lead is NEW, CONTACTED or QUALIFIED until it is converted
 * (it becomes a deal) or disqualified. A deal then moves through the pipeline stages.
 * Deal stages come from the API (GET /pipeline/stages); the labels here are for lead statuses
 * and a fallback for colours.
 */

export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "DISQUALIFIED" | "CONVERTED";

export const LEAD_STATUSES: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "CONVERTED"];

export const STAGE_LABELS: Record<string, string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  DISQUALIFIED: "Disqualified",
  CONVERTED: "Converted",
  DEMO: "Demo",
  PROPOSAL: "Proposal",
  NEGOTIATION: "Negotiation",
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
