"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Download, Plus, Save, Trash2 } from "lucide-react";
import { useState } from "react";
import { Card, buttonPrimary, buttonSecondary, control, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { downloadCsv } from "@/lib/csv";
import type { Overview } from "@/lib/reports";
import type { RevenuePoint } from "@/components/dashboard/revenue-chart";
import { savedReports, type ReportChart, type ReportDataset, type SavedReport } from "@/lib/saved-reports";

const datasets: Record<ReportDataset, string> = { revenue: "Revenue by month", team: "Team revenue", funnel: "Sales funnel", sources: "Revenue by source" };
const colors = ["#4f46e5", "#818cf8", "#37a786", "#e3aa55", "#a96bd3", "#6ba9d3"];

export function ReportBuilder({ overview, revenue }: { overview: Overview | undefined; revenue: RevenuePoint[] }) {
  const client = useQueryClient();
  const [name, setName] = useState("");
  const [dataset, setDataset] = useState<ReportDataset>("revenue");
  const [chart, setChart] = useState<ReportChart>("bar");
  const [active, setActive] = useState<SavedReport | null>(null);
  const reports = useQuery({ queryKey: ["saved-reports"], queryFn: savedReports.list });
  const invalidate = () => client.invalidateQueries({ queryKey: ["saved-reports"] });
  const create = useMutation({ mutationFn: savedReports.create, onSuccess: (item) => { setActive(item); setName(""); void invalidate(); toast("Report saved."); }, onError: () => toast("Report could not be saved.") });
  const remove = useMutation({ mutationFn: savedReports.remove, onSuccess: () => { setActive(null); void invalidate(); toast("Saved report removed."); }, onError: () => toast("Report could not be removed.") });
  const chosenDataset = active?.dataset ?? dataset;
  const chosenChart = active?.chart ?? chart;
  const points = chosenDataset === "revenue" ? revenue.map((item) => ({ label: item.label, value: item.revenue })) : chosenDataset === "team" ? (overview?.team ?? []).map((item) => ({ label: item.name, value: item.revenue })) : chosenDataset === "funnel" ? (overview?.funnel ?? []).map((item) => ({ label: item.label, value: item.count })) : (overview?.revenueBySource ?? []).map((item) => ({ label: item.source, value: item.revenue }));
  const exportData = () => { downloadCsv("nexus-report.csv", ["Category", chosenDataset === "funnel" ? "Count" : "Value (INR)"], points.map((item) => [item.label, item.value])); toast("Report exported."); };
  const choose = (item: SavedReport) => { setActive(item); setDataset(item.dataset); setChart(item.chart); };

  return <Card className="overflow-hidden">
    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4"><div><h2 className="text-[16px] font-semibold">Report builder</h2><p className="mt-1 text-[12px] text-ink-3">Configure a view, save it to the CRM, or export the underlying values.</p></div><button onClick={exportData} disabled={!points.length} className={buttonSecondary}><Download className="size-4" />Export CSV</button></div>
    <div className="grid lg:grid-cols-[230px_minmax(0,1fr)]">
      <aside className="border-b border-line bg-canvas/50 p-4 lg:border-r lg:border-b-0"><div className="flex items-center justify-between"><h3 className="text-[11px] font-semibold uppercase tracking-wide text-ink-3">Saved reports</h3><button onClick={() => { setActive(null); setName(""); }} aria-label="New report" className="rounded p-1 text-brand hover:bg-brand-soft"><Plus className="size-4" /></button></div>{reports.isPending ? <p className="mt-3 text-[12px] text-ink-3">Loading…</p> : reports.data?.length ? <ul className="mt-3 space-y-1">{reports.data.map((item) => <li key={item.id}><button onClick={() => choose(item)} className={`w-full truncate rounded-md px-2 py-2 text-left text-[12.5px] ${active?.id === item.id ? "bg-brand-soft font-medium text-brand" : "text-ink-2 hover:bg-surface"}`}>{item.name}</button></li>)}</ul> : <p className="mt-3 text-[12px] text-ink-3">No reports saved yet.</p>}</aside>
      <div className="min-w-0"><div className="flex flex-wrap items-end gap-3 border-b border-line p-4"><label className="min-w-48 flex-1 text-[11px] font-medium text-ink-3">Dataset<select value={dataset} onChange={(event) => { setActive(null); setDataset(event.target.value as ReportDataset); }} className={control + " mt-1 w-full px-2"}>{Object.entries(datasets).map(([id, title]) => <option key={id} value={id}>{title}</option>)}</select></label><label className="min-w-28 text-[11px] font-medium text-ink-3">Chart<select value={chart} onChange={(event) => { setActive(null); setChart(event.target.value as ReportChart); }} className={control + " mt-1 w-full px-2"}><option value="bar">Bar</option><option value="line">Line</option><option value="pie">Pie</option></select></label></div>
        <div className="p-4"><h3 className="text-[13px] font-medium">{active?.name ?? datasets[chosenDataset]}</h3><p className="mt-1 text-[11px] text-ink-3">{points.length} categories · {chosenChart} chart</p>{points.length ? <div className="mt-4 h-64" role="img" aria-label={`${datasets[chosenDataset]} ${chosenChart} chart. ${points.map((item) => `${item.label}: ${item.value}`).join(", ")}`}><ResponsiveContainer width="100%" height="100%">{chosenChart === "pie" ? <PieChart><Pie data={points} dataKey="value" nameKey="label" innerRadius={48} outerRadius={85} paddingAngle={2}>{points.map((item, index) => <Cell key={item.label} fill={colors[index % colors.length]} />)}</Pie><Tooltip /></PieChart> : chosenChart === "line" ? <LineChart data={points} margin={{ top: 10, right: 18, bottom: 12, left: 8 }}><CartesianGrid vertical={false} stroke="var(--line)" /><XAxis dataKey="label" tick={{ fill: "var(--ink-3)", fontSize: 11 }} /><YAxis tick={{ fill: "var(--ink-3)", fontSize: 11 }} width={50} /><Tooltip /><Line type="monotone" dataKey="value" stroke="var(--brand)" strokeWidth={2} dot={{ r: 3 }} animationDuration={250} /></LineChart> : <BarChart data={points} margin={{ top: 10, right: 18, bottom: 12, left: 8 }}><CartesianGrid vertical={false} stroke="var(--line)" /><XAxis dataKey="label" tick={{ fill: "var(--ink-3)", fontSize: 11 }} /><YAxis tick={{ fill: "var(--ink-3)", fontSize: 11 }} width={50} /><Tooltip /><Bar dataKey="value" fill="var(--brand)" radius={[4, 4, 0, 0]} animationDuration={250} /></BarChart>}</ResponsiveContainer></div> : <p className="mt-4 rounded-lg border border-dashed border-line p-8 text-center text-[12px] text-ink-3">No data for this selection and date range.</p>}<div className="mt-4 flex flex-wrap items-end gap-2 border-t border-line pt-4"><label className="min-w-48 flex-1 text-[11px] font-medium text-ink-3">Report name<input value={name} onChange={(event) => setName(event.target.value)} maxLength={80} placeholder={datasets[dataset]} className={fieldControl} /></label><button onClick={() => create.mutate({ name: name.trim() || datasets[dataset], dataset, chart })} disabled={create.isPending} className={buttonPrimary}><Save className="size-4" />{create.isPending ? "Saving…" : "Save report"}</button>{active && <button onClick={() => remove.mutate(active.id)} disabled={remove.isPending} aria-label={`Remove ${active.name}`} className={buttonSecondary}><Trash2 className="size-4" /></button>}</div></div>
      </div>
    </div>
  </Card>;
}
