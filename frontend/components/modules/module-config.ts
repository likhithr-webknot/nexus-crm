import type { ModuleKind } from "@/lib/modules";

export const MODULES: Record<ModuleKind, { title: string; singular: string; subtitle: string; statuses: string[]; metric: string; note: string }> = {
  campaigns: { title: "Campaigns", singular: "campaign", subtitle: "Track campaign plans and budgets.", statuses: ["Draft", "Scheduled", "Active", "Completed"], metric: "Budget", note: "Campaign plans are saved to the CRM. Audience delivery and engagement tracking are still in development." },
  products: { title: "Products", singular: "product", subtitle: "A catalog for pricing and quote building.", statuses: ["Active", "Archived"], metric: "Price", note: "Products are saved to the CRM." },
  quotes: { title: "Quotes", singular: "quote", subtitle: "Prepare and track proposals with account context.", statuses: ["Draft", "In review", "Approved", "Declined"], metric: "Total", note: "Quote totals are recalculated and saved by the server." },
  communications: { title: "Communications", singular: "email", subtitle: "Write and send customer email from your connected mailbox.", statuses: ["Draft", "Sending", "Sent", "Failed", "Unknown"], metric: "", note: "Email drafts are saved in the CRM and deleted 30 days after creation. An unknown send status needs a Sent mail check before any retry." },
  automations: { title: "Automations", singular: "workflow", subtitle: "Define repeatable actions and inspect their state.", statuses: ["Draft", "Published", "Paused"], metric: "Runs", note: "Workflow definitions are saved to the CRM." },
};
