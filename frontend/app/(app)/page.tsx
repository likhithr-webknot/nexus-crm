import Link from "next/link";
import { CalendarDays, Plus } from "lucide-react";
import { Card, CardHeader, CardLink } from "@/components/ui/primitives";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { RevenueChart } from "@/components/dashboard/revenue-chart";
import { LeadSourcesDonut } from "@/components/dashboard/lead-sources";
import { RecentLeadsTable } from "@/components/dashboard/recent-leads";
import { PipelineOverview } from "@/components/dashboard/pipeline-overview";
import { FollowUpList } from "@/components/dashboard/follow-ups";
import {
  CURRENT_USER,
  FOLLOW_UPS,
  KPIS,
  LEAD_SOURCES,
  PIPELINE,
  RECENT_LEADS,
  REVENUE,
} from "@/lib/mock-data";
import { formatUsd } from "@/lib/format";

export const metadata = { title: "Dashboard" };

export default function DashboardPage() {
  const totalRevenue = REVENUE.reduce((a, r) => a + r.revenue, 0);
  const totalTarget = REVENUE.reduce((a, r) => a + r.target, 0);
  const attainment = Math.round((totalRevenue / totalTarget) * 100);
  const pipelineValue = PIPELINE.reduce((a, s) => a + s.value, 0);
  const firstName = CURRENT_USER.name.split(" ")[0];

  return (
    <div className="mx-auto max-w-[1600px] space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[24px] font-semibold tracking-[-0.025em] text-ink">Welcome back, {firstName}</h1>
          <p className="mt-1 text-[14px] text-ink-2">Here&apos;s how your pipeline is tracking this month.</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="inline-flex h-9 items-center gap-2 rounded-lg border border-line bg-surface px-3 text-[13px] font-medium text-ink-2 hover:border-line-strong hover:text-ink">
            <CalendarDays className="size-4" aria-hidden />
            Jun 1 – Jun 30, 2025
          </button>
          <Link
            href="/leads"
            className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-brand px-3.5 text-[13px] font-semibold text-white shadow-sm hover:bg-brand-ink"
          >
            <Plus className="size-4" aria-hidden />
            Add lead
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-[repeat(auto-fit,minmax(156px,1fr))] gap-3">
        {KPIS.map((kpi) => (
          <KpiCard key={kpi.id} kpi={kpi} />
        ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Revenue performance"
            subtitle="Monthly revenue against target"
            action={<CardLink href="/reports">Last 6 months</CardLink>}
          />
          <div className="flex flex-wrap items-end justify-between gap-4 px-5">
            <div>
              <p className="text-[12px] font-medium text-ink-3">Revenue achieved</p>
              <p className="tnum mt-1 text-[26px] leading-none font-semibold tracking-[-0.02em]">
                {formatUsd(totalRevenue)}
              </p>
              <p className="mt-1.5 text-[12px] text-ink-3">
                <span className="font-semibold text-up">{attainment}%</span> of {formatUsd(totalTarget)} target
              </p>
            </div>
            <div className="flex items-center gap-4 text-[12px] text-ink-2">
              <span className="flex items-center gap-1.5">
                <span className="h-0.5 w-4 rounded bg-brand" aria-hidden /> Revenue
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-4 border-t-2 border-dashed border-slate-400" aria-hidden /> Target
              </span>
            </div>
          </div>
          <div className="px-3 pt-2 pb-3">
            <RevenueChart data={REVENUE} />
          </div>
        </Card>

        <Card>
          <CardHeader
            title="Lead sources"
            subtitle="Distribution by channel"
            action={<CardLink href="/reports">This month</CardLink>}
          />
          <div className="px-5 pt-2 pb-5">
            <LeadSourcesDonut sources={LEAD_SOURCES} total={KPIS[0].value} />
          </div>
        </Card>
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="overflow-hidden lg:col-span-2">
          <CardHeader
            title="Recent leads"
            subtitle="Latest additions to your pipeline"
            action={<CardLink href="/leads">View all leads</CardLink>}
          />
          <RecentLeadsTable leads={RECENT_LEADS} />
        </Card>

        <div className="space-y-5">
          <Card>
            <CardHeader
              title="Pipeline overview"
              subtitle={`${formatUsd(pipelineValue)} total pipeline value`}
              action={<CardLink href="/pipeline">View pipeline</CardLink>}
            />
            <div className="px-5 pt-1 pb-5">
              <PipelineOverview stages={PIPELINE} />
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Today's follow-ups"
              subtitle={`${FOLLOW_UPS.length} scheduled · ${FOLLOW_UPS.filter((t) => t.overdue).length} overdue`}
              action={<CardLink href="/follow-ups">All</CardLink>}
            />
            <FollowUpList tasks={FOLLOW_UPS} />
          </Card>
        </div>
      </div>
    </div>
  );
}
