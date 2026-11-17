import type { RevenuePoint } from "@/components/dashboard/revenue-chart";
import { KEYS } from "@/lib/query-keys";
import { revenueByMonth } from "@/lib/reports";
import { useQuery } from "@tanstack/react-query";

const monthShort = new Intl.DateTimeFormat("en-US", { month: "short", timeZone: "UTC" });
const monthFull = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" });

/** Revenue for the last months with each month's target, shaped for the chart. */
export function useRevenuePoints(months = 6) {
  const query = useQuery({ queryKey: [...KEYS.dashboard, "revenue", months], queryFn: () => revenueByMonth(months) });
  const points: RevenuePoint[] = (query.data ?? []).map((m) => {
    const first = new Date(`${m.month}-01T00:00:00Z`);
    return {
      month: monthFull.format(first),
      label: monthShort.format(first),
      revenue: m.revenue,
      target: m.target == null ? null : Number(m.target),
    };
  });
  const withTarget = points.filter((p) => p.target != null);
  return {
    query,
    points,
    totalRevenue: points.reduce((sum, p) => sum + p.revenue, 0),
    hasTargets: withTarget.length > 0,
    totalTarget: withTarget.reduce((sum, p) => sum + (p.target ?? 0), 0),
    revenueWithTarget: withTarget.reduce((sum, p) => sum + p.revenue, 0),
  };
}
