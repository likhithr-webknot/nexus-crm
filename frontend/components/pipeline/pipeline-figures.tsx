import { useCurrency } from "@/components/currency/currency-provider";
import type { Deal, PipelineStage } from "@/lib/deals";
import { isOpen, sumInReporting, weightedForecast, winRate } from "@/lib/pipeline-math";

/** The four numbers above the board: open pipeline, weighted forecast, won, and win rate. */
export function PipelineFigures({ deals, stages }: { deals: Deal[]; stages: Map<string, PipelineStage> }) {
  const { reportingCurrency, convertToReporting, format } = useCurrency();
  const open = deals.filter(isOpen);
  const won = deals.filter((d) => d.stageCode === "WON");
  const lost = deals.filter((d) => d.stageCode === "LOST");

  const money = (amount: number | null) => (amount == null ? "—" : format(Math.round(amount), reportingCurrency));
  const rate = winRate(won.length, lost.length);

  const figures = [
    { label: "Open pipeline", value: money(sumInReporting(open, convertToReporting)), note: `${open.length} deals` },
    {
      label: "Weighted forecast",
      value: money(weightedForecast(open, stages, convertToReporting)),
      note: "Value × chance to win",
    },
    { label: "Won", value: money(sumInReporting(won, convertToReporting)), note: `${won.length} deals, last 90 days` },
    { label: "Win rate", value: rate == null ? "–" : `${rate}%`, note: "Of closed deals" },
  ];

  return (
    <dl className="grid flex-1 grid-cols-2 gap-x-8 gap-y-4 sm:flex sm:flex-none">
      {figures.map((f) => (
        <div key={f.label}>
          <dt className="text-[12px] font-medium text-ink-3">{f.label}</dt>
          <dd className="tnum mt-1 text-[20px] leading-none font-semibold tracking-[-0.02em] text-ink">{f.value}</dd>
          <dd className="mt-1.5 text-[12px] text-ink-3">{f.note}</dd>
        </div>
      ))}
    </dl>
  );
}
