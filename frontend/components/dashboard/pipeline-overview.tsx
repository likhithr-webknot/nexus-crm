import type { PipelineStage } from "@/lib/mock-data";
import { formatCompactUsd } from "@/lib/format";

const BAR_COLORS = ["#4f46e5", "#6366f1", "#818cf8", "#a5b4fc", "#10b981"];

export function PipelineOverview({ stages }: { stages: PipelineStage[] }) {
  const max = Math.max(...stages.map((s) => s.count));
  return (
    <ul className="space-y-3.5">
      {stages.map((s, i) => {
        const prev = stages[i - 1];
        // Stage-to-stage conversion is what a sales lead actually acts on.
        const conv = prev ? Math.round((s.count / prev.count) * 100) : null;
        return (
          <li key={s.stage}>
            <div className="mb-1.5 flex items-baseline justify-between text-[13px]">
              <span className="font-medium text-ink">{s.stage}</span>
              <span className="tnum text-ink-3">
                <span className="font-semibold text-ink">{s.count}</span> · {formatCompactUsd(s.value)}
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
