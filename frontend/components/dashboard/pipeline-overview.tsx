import { useCurrency } from "@/components/currency/currency-provider";

const BAR_COLORS = ["#4f46e5", "#6366f1", "#818cf8", "#a5b4fc", "#10b981"];

export function PipelineOverview({
  stages,
}: {
  stages: { stageCode: string; name: string; count: number; amount: number }[];
}) {
  const { formatCompactReporting } = useCurrency();
  const max = Math.max(1, ...stages.map((s) => s.count));
  return (
    <ul className="space-y-3.5">
      {stages.map((s, i) => {
        const prev = stages[i - 1];
        // Stage-to-stage conversion is what a sales lead actually acts on.
        const conv = prev && prev.count > 0 ? Math.round((s.count / prev.count) * 100) : null;
        return (
          <li key={s.stageCode}>
            <div className="mb-1.5 flex items-baseline justify-between text-[13px]">
              <span className="font-medium text-ink">{s.name}</span>
              <span className="tnum text-ink-3">
                <span className="font-semibold text-ink">{s.count}</span> ·{" "}
                {formatCompactReporting(s.amount, "INR") ?? "—"}
              </span>
            </div>
            <div className="flex items-center gap-3">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-canvas">
                <div
                  className="h-full rounded-full"
                  style={{ width: `${(s.count / max) * 100}%`, background: BAR_COLORS[i % BAR_COLORS.length] }}
                />
              </div>
              <span className="tnum w-9 text-right text-[11.5px] text-ink-3" title="Conversion from previous stage">
                {conv != null ? `${conv}%` : ""}
              </span>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
