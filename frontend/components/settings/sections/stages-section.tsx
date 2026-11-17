"use client";

import { Badge, Card, CardHeader, STAGE_DOT } from "@/components/ui/primitives";
import { listStages } from "@/lib/deals";
import { cn } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";

export function StagesSection() {
  const stages = useQuery({ queryKey: KEYS.stages, queryFn: listStages, staleTime: Infinity });
  return (
    <Card>
      <CardHeader
        title="Pipeline stages"
        subtitle="One pipeline with fixed stages in this version. The chance to win drives the weighted forecast."
      />
      {stages.isPending ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Loading…</p>
      ) : (
        <ol className="divide-y divide-line border-t border-line">
          {(stages.data ?? []).map((stage) => (
            <li key={stage.code} className="flex items-center gap-3 px-5 py-3 text-[13px]">
              <span className="tnum w-4 text-ink-3">{stage.position}</span>
              <span className={cn("size-2 rounded-full", STAGE_DOT[stage.code])} aria-hidden />
              <span className="font-medium text-ink">{stage.name}</span>
              {stage.kind !== "OPEN" && <Badge>Closed</Badge>}
              <span className="tnum ml-auto text-ink-2">{stage.winProbability}% chance to win</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
