// Static fixtures until the API exists. Shapes mirror what the backend
// is expected to return so components won't change when it's wired up.

export type Kpi = {
  id: string;
  label: string;
  value: string;
  delta: number; // percent vs last period
  trend: number[];
  /** For metrics where a decrease is good (e.g. deals lost). */
  invert?: boolean;
};

export const KPIS: Kpi[] = [
  { id: "total", label: "Total Leads", value: "2,846", delta: 12.5, trend: [12, 14, 13, 17, 16, 19, 22] },
  { id: "new", label: "New Leads", value: "384", delta: 8.2, trend: [8, 9, 7, 10, 12, 11, 13] },
  { id: "qualified", label: "Qualified Leads", value: "1,128", delta: 14.8, trend: [10, 11, 13, 12, 15, 16, 18] },
  { id: "won", label: "Deals Won", value: "186", delta: 6.4, trend: [5, 7, 6, 8, 7, 9, 10] },
  { id: "lost", label: "Deals Lost", value: "42", delta: -4.1, trend: [9, 8, 9, 7, 8, 6, 5], invert: true },
  { id: "revenue", label: "Revenue", value: "$1.28M", delta: 18.2, trend: [9, 10, 12, 11, 14, 16, 19] },
  { id: "conversion", label: "Conversion Rate", value: "24.8%", delta: 2.3, trend: [20, 21, 21, 22, 23, 24, 25] },
];

export type RevenuePoint = { month: string; revenue: number; target: number };

export const REVENUE: RevenuePoint[] = [
  { month: "Jan", revenue: 142_000, target: 150_000 },
  { month: "Feb", revenue: 168_000, target: 165_000 },
  { month: "Mar", revenue: 191_000, target: 180_000 },
  { month: "Apr", revenue: 214_000, target: 200_000 },
  { month: "May", revenue: 248_200, target: 225_000 },
  { month: "Jun", revenue: 321_000, target: 250_000 },
];

export type LeadSource = { label: string; share: number; color: string };

export const LEAD_SOURCES: LeadSource[] = [
  { label: "Website", share: 34, color: "#4f46e5" },
  { label: "Social Media", share: 24, color: "#38bdf8" },
  { label: "Referrals", share: 18, color: "#a78bfa" },
  { label: "Campaigns", share: 16, color: "#f59e0b" },
  { label: "Manual Entry", share: 8, color: "#94a3b8" },
];

export type Stage = "New" | "Contacted" | "Qualified" | "Demo" | "Proposal" | "Negotiation" | "Won" | "Lost";

export const STAGES: Stage[] = ["New", "Contacted", "Qualified", "Demo", "Proposal", "Negotiation", "Won", "Lost"];

export type Source = "Website" | "Referral" | "Campaign" | "Social Media" | "Manual Entry";

export const SOURCES: Source[] = ["Website", "Referral", "Campaign", "Social Media", "Manual Entry"];

export type Lead = {
  id: string;
  name: string;
  company: string;
  email: string;
  phone: string;
  source: Source;
  /** null means nobody has picked the lead up yet. */
  owner: string | null;
  stage: Stage;
  value: number;
  createdAt: string; // ISO date
};

// The fixtures are frozen in June 2025, so "this month" is fixed too.
export const THIS_MONTH = "2025-06";

// Change vs last month for the tiles on the Leads page.
export const LEAD_VIEW_DELTAS = { all: 12.5, new: 8.2, qualified: 14.8, unassigned: -6.4 };

const lead = (
  n: number,
  name: string,
  company: string,
  phone: string,
  source: Source,
  owner: string | null,
  stage: Stage,
  value: number,
  createdAt: string,
): Lead => {
  const domain = company.toLowerCase().replace(/[^a-z]/g, "");
  const email = `${name.split(" ")[0].toLowerCase()}@${domain}.example`;
  return { id: `L-${n}`, name, company, email, phone: `+1 ${phone}`, source, owner, stage, value, createdAt };
};

// Newest first.
export const LEADS: Lead[] = [
  lead(1042, "Olivia Martin", "Apex Dynamics", "(415) 555-0143", "Website", "Sarah Chen", "Qualified", 48_000, "2025-06-12"),
  lead(1041, "Marcus Thompson", "Vertex Labs", "(212) 555-0121", "Referral", "James Wilson", "Demo", 32_500, "2025-06-11"),
  lead(1040, "Sophia Rodriguez", "Lumina Health", "(310) 555-0108", "Campaign", "Sarah Chen", "Proposal", 76_000, "2025-06-10"),
  lead(1039, "Ethan Brooks", "Northstar AI", "(617) 555-0183", "Social Media", "Maya Patel", "Contacted", 18_200, "2025-06-09"),
  lead(1038, "Amelia Foster", "Cloudwell", "(646) 555-0110", "Website", "James Wilson", "Negotiation", 64_000, "2025-06-08"),
  lead(1037, "Daniel Kim", "Meridian Systems", "(206) 555-0102", "Manual Entry", "Maya Patel", "New", 12_000, "2025-06-07"),
  lead(1036, "Nora Bennett", "Field & Form", "(512) 555-0137", "Referral", "Sarah Chen", "Won", 94_000, "2025-06-06"),
  lead(1035, "Liam Carter", "Orbital Finance", "(917) 555-0120", "Campaign", "James Wilson", "Lost", 27_500, "2025-06-04"),
  lead(1034, "Priya Nair", "Solace Energy", "(408) 555-0164", "Website", null, "New", 22_000, "2025-06-04"),
  lead(1033, "Emma Larsson", "Fjord Analytics", "(503) 555-0175", "Social Media", "Maya Patel", "Qualified", 41_000, "2025-06-03"),
  lead(1032, "Ravi Menon", "Northwind Freight", "(312) 555-0191", "Referral", "Sarah Chen", "Proposal", 58_500, "2025-06-03"),
  lead(1031, "Grace Okafor", "Brightline Media", "(404) 555-0116", "Campaign", null, "New", 9_800, "2025-06-02"),
  lead(1030, "Lucas Meyer", "Kestrel Robotics", "(720) 555-0158", "Website", "James Wilson", "Contacted", 36_000, "2025-06-02"),
  lead(1029, "Hannah Schultz", "Pinecrest Legal", "(602) 555-0149", "Manual Entry", "Maya Patel", "Demo", 29_400, "2025-06-01"),
  lead(1028, "Omar Haddad", "Tidewater Logistics", "(757) 555-0133", "Referral", "Sarah Chen", "Qualified", 67_000, "2025-05-30"),
  lead(1027, "Isabella Rossi", "Verde Foods", "(305) 555-0187", "Social Media", null, "Contacted", 14_500, "2025-05-29"),
  lead(1026, "Noah Williams", "Granite Peak Capital", "(801) 555-0172", "Website", "James Wilson", "Negotiation", 112_000, "2025-05-28"),
  lead(1025, "Mei Tanaka", "Lantern Studio", "(213) 555-0105", "Campaign", "Maya Patel", "Won", 38_000, "2025-05-27"),
  lead(1024, "Carlos Vega", "Summit Outdoor", "(303) 555-0169", "Website", "Sarah Chen", "Lost", 19_000, "2025-05-23"),
  lead(1023, "Aisha Rahman", "Harbor Health", "(410) 555-0154", "Referral", "James Wilson", "Qualified", 83_000, "2025-05-22"),
  lead(1022, "Jack Sullivan", "Ironwood Builders", "(615) 555-0128", "Manual Entry", null, "New", 24_000, "2025-05-21"),
  lead(1021, "Chloe Dubois", "Atelier Nine", "(504) 555-0196", "Social Media", "Maya Patel", "Proposal", 31_500, "2025-05-20"),
  lead(1020, "Tomás Herrera", "Quanta Grid", "(512) 555-0111", "Campaign", "Sarah Chen", "Contacted", 45_000, "2025-05-16"),
  lead(1019, "Zoe Anderson", "Maple & Main", "(612) 555-0147", "Website", "James Wilson", "Demo", 16_800, "2025-05-14"),
];

export const RECENT_LEADS: Lead[] = LEADS.slice(0, 6);

export type PipelineStage = { stage: Stage; count: number; value: number };

export const PIPELINE: PipelineStage[] = [
  { stage: "New", count: 482, value: 214_000 },
  { stage: "Contacted", count: 346, value: 188_500 },
  { stage: "Qualified", count: 248, value: 162_000 },
  { stage: "Proposal", count: 142, value: 148_000 },
  { stage: "Won", count: 86, value: 112_000 },
];

// ---- Follow-ups ------------------------------------------------------------

// The fixtures are frozen in time, so "today" is fixed too.
export const TODAY = "2025-06-16";

export type FollowUpType = "Call" | "Meeting" | "Email" | "Task";

export type FollowUp = {
  id: string;
  title: string;
  type: FollowUpType;
  contact: string;
  company: string;
  owner: string;
  dueAt: string; // local date and time, "2025-06-16T09:30"
  done: boolean;
};

const followUp = (
  id: number,
  dueAt: string,
  type: FollowUpType,
  title: string,
  leadIndex: number,
  done = false,
): FollowUp => {
  const l = LEADS[leadIndex];
  return { id: `f${id}`, title, type, contact: l.name, company: l.company, owner: l.owner ?? "Sarah Chen", dueAt, done };
};

export const FOLLOW_UPS: FollowUp[] = [
  followUp(1, "2025-06-03T10:00", "Call", "Intro call", 9, true),
  followUp(2, "2025-06-05T15:30", "Email", "Send case studies", 12, true),
  followUp(3, "2025-06-09T11:00", "Meeting", "Product demo", 13, true),
  followUp(4, "2025-06-11T14:00", "Call", "Pricing questions", 14, true),
  followUp(5, "2025-06-12T09:00", "Task", "Prepare security questionnaire", 16),
  followUp(6, "2025-06-13T16:00", "Email", "Contract follow-up", 1),
  followUp(7, "2025-06-13T11:30", "Call", "Re-engage after no-show", 15),
  followUp(8, "2025-06-16T09:30", "Call", "Discovery call", 0),
  followUp(9, "2025-06-16T11:00", "Meeting", "Demo with the buying team", 1),
  followUp(10, "2025-06-16T14:30", "Email", "Send revised proposal", 2),
  followUp(11, "2025-06-16T16:00", "Task", "Update deal notes", 4, true),
  followUp(12, "2025-06-17T10:30", "Call", "Check in on budget approval", 10),
  followUp(13, "2025-06-18T10:00", "Meeting", "Negotiation review", 4),
  followUp(14, "2025-06-18T15:00", "Email", "Share onboarding plan", 6),
  followUp(15, "2025-06-19T13:00", "Call", "First call", 5),
  followUp(16, "2025-06-20T09:30", "Meeting", "Technical deep dive", 19),
  followUp(17, "2025-06-23T11:00", "Call", "Quarterly check-in", 17),
  followUp(18, "2025-06-24T14:00", "Task", "Draft renewal terms", 16),
  followUp(19, "2025-06-26T10:00", "Meeting", "Proposal walkthrough", 21),
  followUp(20, "2025-06-30T16:30", "Email", "Month-end pipeline recap", 3),
  followUp(21, "2025-07-02T10:00", "Call", "Kick-off planning", 6),
];

// ---- Deals -----------------------------------------------------------------

/** Chance of winning a deal at each stage, used for the weighted forecast. */
export const STAGE_PROBABILITY: Record<Stage, number> = {
  New: 10,
  Contacted: 20,
  Qualified: 40,
  Demo: 55,
  Proposal: 70,
  Negotiation: 85,
  Won: 100,
  Lost: 0,
};

export type Priority = "High" | "Medium" | "Low";

export type Deal = {
  id: string;
  contact: string;
  company: string;
  owner: string | null;
  stage: Stage;
  value: number;
  priority: Priority;
  updatedAt: string; // ISO date
};

export const DEALS: Deal[] = LEADS.map((l) => ({
  id: l.id.replace("L", "D"),
  contact: l.name,
  company: l.company,
  owner: l.owner,
  stage: l.stage,
  value: l.value,
  priority: l.value >= 60_000 ? "High" : l.value >= 30_000 ? "Medium" : "Low",
  updatedAt: l.createdAt,
}));

// ---- Contacts --------------------------------------------------------------

const JOB_TITLES = ["VP of Sales", "Head of Operations", "CTO", "Procurement Lead", "Founder", "Marketing Director"];

export type Contact = Pick<Lead, "id" | "name" | "company" | "email" | "phone" | "owner" | "stage" | "value"> & {
  title: string;
};

export const CONTACTS: Contact[] = LEADS.map((l, i) => ({
  id: l.id.replace("L", "C"),
  name: l.name,
  company: l.company,
  email: l.email,
  phone: l.phone,
  owner: l.owner,
  stage: l.stage,
  value: l.value,
  title: JOB_TITLES[i % JOB_TITLES.length],
})).sort((a, b) => a.name.localeCompare(b.name));

// ---- Activities ------------------------------------------------------------

export type ActivityType = "Call" | "Email" | "Meeting" | "Note" | "System";

export type Activity = {
  id: string;
  type: ActivityType;
  title: string;
  detail: string;
  contact: string;
  company: string;
  /** Who did it. System entries have no person behind them. */
  actor: string | null;
  at: string; // local date and time
};

const activity = (id: number, at: string, type: ActivityType, title: string, detail: string, leadIndex: number): Activity => {
  const l = LEADS[leadIndex];
  return {
    id: `a${id}`,
    type,
    title,
    detail,
    contact: l.name,
    company: l.company,
    actor: type === "System" ? null : (l.owner ?? "Sarah Chen"),
    at,
  };
};

// Newest first.
export const ACTIVITIES: Activity[] = [
  activity(1, "2025-06-16T10:12", "Call", "Discovery call completed", "Confirmed budget and a Q3 start. Wants a security review before the demo.", 0),
  activity(2, "2025-06-16T09:40", "System", "Stage changed to Negotiation", "Moved from Proposal.", 4),
  activity(3, "2025-06-16T08:55", "Email", "Sent demo agenda", "Shared the agenda and the attendee list for today's 11:00 demo.", 1),
  activity(4, "2025-06-15T17:20", "Note", "Note added", "Legal is reviewing the MSA. Expect redlines by Wednesday.", 2),
  activity(5, "2025-06-15T14:05", "Meeting", "Pricing workshop", "Walked through the three tiers. They lean towards annual billing.", 4),
  activity(6, "2025-06-15T11:30", "System", "Lead assigned", "Assigned to Maya Patel from the unassigned queue.", 5),
  activity(7, "2025-06-13T16:45", "Call", "Left a voicemail", "No answer. Follow-up email sent straight after.", 3),
  activity(8, "2025-06-13T13:10", "Email", "Proposal sent", "Version 2 with the revised seat count.", 10),
  activity(9, "2025-06-13T09:15", "System", "Deal won", "Closed at $94,000.", 6),
  activity(10, "2025-06-12T15:00", "Meeting", "Technical deep dive", "SSO and data residency were the main questions. Both covered.", 19),
  activity(11, "2025-06-12T10:25", "Note", "Note added", "Champion is leaving in August. Need a second contact on their side.", 16),
  activity(12, "2025-06-11T14:00", "Call", "Pricing questions", "Asked about volume discounts beyond 200 seats.", 14),
  activity(13, "2025-06-10T12:40", "System", "Stage changed to Lost", "Reason: went with a competitor on price.", 7),
  activity(14, "2025-06-10T09:05", "Email", "Intro email", "Replied within the hour and asked for a call next week.", 12),
  activity(15, "2025-06-09T11:00", "Meeting", "Product demo", "Strong interest in reporting. Requested a sandbox.", 13),
];

// ---- Team ------------------------------------------------------------------

export type Role = "Admin" | "Manager" | "Rep";

export type TeamMember = {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: "Active" | "Invited" | "Disabled";
  leads: number;
  dealsWon: number;
  revenue: number;
  /** Share of leads that became won deals, in percent. */
  conversion: number;
  /** Revenue against quota, in percent. */
  attainment: number;
  lastActive: string | null; // ISO date
};

export const TEAM: TeamMember[] = [
  { id: "u1", name: "Sarah Chen", email: "sarah@nexuscrm.example", role: "Manager", status: "Active", leads: 412, dealsWon: 42, revenue: 384_200, conversion: 31.2, attainment: 92, lastActive: "2025-06-16" },
  { id: "u2", name: "James Wilson", email: "james@nexuscrm.example", role: "Rep", status: "Active", leads: 386, dealsWon: 38, revenue: 328_500, conversion: 28.4, attainment: 86, lastActive: "2025-06-16" },
  { id: "u3", name: "Maya Patel", email: "maya@nexuscrm.example", role: "Rep", status: "Active", leads: 354, dealsWon: 34, revenue: 298_400, conversion: 26.8, attainment: 79, lastActive: "2025-06-15" },
  { id: "u4", name: "Noah Williams", email: "noah@nexuscrm.example", role: "Admin", status: "Active", leads: 298, dealsWon: 29, revenue: 246_800, conversion: 23.5, attainment: 72, lastActive: "2025-06-13" },
  { id: "u5", name: "Lena Fischer", email: "lena@nexuscrm.example", role: "Rep", status: "Invited", leads: 0, dealsWon: 0, revenue: 0, conversion: 0, attainment: 0, lastActive: null },
  { id: "u6", name: "Owen Clarke", email: "owen@nexuscrm.example", role: "Rep", status: "Disabled", leads: 0, dealsWon: 0, revenue: 0, conversion: 0, attainment: 0, lastActive: "2025-04-28" },
];

/** People a lead, deal or follow-up can be assigned to. */
export const OWNERS = TEAM.filter((m) => m.status === "Active").map((m) => m.name);

// ---- Reports ---------------------------------------------------------------

export const FUNNEL = [
  { label: "New leads", count: 2846 },
  { label: "Contacted", count: 2104 },
  { label: "Qualified", count: 1128 },
  { label: "Proposal", count: 548 },
  { label: "Won", count: 186 },
];

export const REVENUE_BY_SOURCE = [
  { label: "Website", revenue: 436_600 },
  { label: "Referrals", revenue: 334_000 },
  { label: "Campaigns", revenue: 256_800 },
  { label: "Social Media", revenue: 167_000 },
  { label: "Manual Entry", revenue: 89_800 },
];

export const COMPANY = {
  name: "Nexus Technologies Inc.",
  industry: "Software & Technology",
  email: "hello@nexuscrm.example",
  phone: "+1 (415) 555-0198",
  address: "548 Market Street, San Francisco, CA 94104",
};
