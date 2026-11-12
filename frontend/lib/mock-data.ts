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

export type Stage = "New" | "Contacted" | "Qualified" | "Demo" | "Proposal" | "Won" | "Lost";

export type Lead = {
  id: string;
  name: string;
  company: string;
  email: string;
  owner: string;
  stage: Stage;
  value: number;
  createdAt: string; // ISO date
};

export const RECENT_LEADS: Lead[] = [
  { id: "L-1042", name: "Olivia Martin", company: "Apex Dynamics", email: "olivia@apexdynamics.example", owner: "Sarah Chen", stage: "Qualified", value: 48_000, createdAt: "2025-06-12" },
  { id: "L-1041", name: "Marcus Thompson", company: "Vertex Labs", email: "marcus@vertexlabs.example", owner: "James Wilson", stage: "Demo", value: 32_500, createdAt: "2025-06-11" },
  { id: "L-1040", name: "Sophia Rodriguez", company: "Lumina Health", email: "sophia@luminahealth.example", owner: "Sarah Chen", stage: "Proposal", value: 76_000, createdAt: "2025-06-10" },
  { id: "L-1039", name: "Daniel Kim", company: "Northwind Freight", email: "d.kim@northwind.example", owner: "Priya Nair", stage: "Contacted", value: 18_200, createdAt: "2025-06-09" },
  { id: "L-1038", name: "Emma Larsson", company: "Fjord Analytics", email: "emma@fjordanalytics.example", owner: "James Wilson", stage: "New", value: 12_000, createdAt: "2025-06-09" },
  { id: "L-1037", name: "Ravi Menon", company: "Solace Energy", email: "ravi@solace.example", owner: "Priya Nair", stage: "Won", value: 94_000, createdAt: "2025-06-08" },
];

export type PipelineStage = { stage: Stage; count: number; value: number };

export const PIPELINE: PipelineStage[] = [
  { stage: "New", count: 482, value: 214_000 },
  { stage: "Contacted", count: 346, value: 188_500 },
  { stage: "Qualified", count: 248, value: 162_000 },
  { stage: "Proposal", count: 142, value: 148_000 },
  { stage: "Won", count: 86, value: 112_000 },
];

export type Task = { id: string; title: string; who: string; due: string; overdue?: boolean };

export const FOLLOW_UPS: Task[] = [
  { id: "t1", title: "Send revised proposal", who: "Lumina Health", due: "Today, 2:00 PM", overdue: false },
  { id: "t2", title: "Discovery call", who: "Fjord Analytics", due: "Today, 4:30 PM" },
  { id: "t3", title: "Contract follow-up", who: "Vertex Labs", due: "Yesterday", overdue: true },
  { id: "t4", title: "Pricing walkthrough", who: "Apex Dynamics", due: "Tomorrow, 11:00 AM" },
];

export const CURRENT_USER = { name: "Alex Morgan", role: "Sales Director", initials: "AM" };
