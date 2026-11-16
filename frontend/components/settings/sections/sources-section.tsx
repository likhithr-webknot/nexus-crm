"use client";

import { Card, CardHeader } from "@/components/ui/primitives";
import { SOURCES, SOURCE_COLORS, SOURCE_LABELS } from "@/lib/stages";


export function SourcesSection() {
  return (
    <Card>
      <CardHeader title="Lead sources" subtitle="Where a lead can come from. Reports show each source's share of new leads." />
      <ul className="divide-y divide-line border-t border-line">
        {SOURCES.map((source) => (
          <li key={source} className="flex items-center gap-3 px-5 py-3 text-[13px]">
            <span className="size-2.5 rounded-sm" style={{ background: SOURCE_COLORS[source] }} aria-hidden />
            <span className="font-medium text-ink">{SOURCE_LABELS[source]}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
