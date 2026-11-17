import { useCurrency } from "@/components/currency/currency-provider";
import { STAGE_DOT } from "@/components/ui/primitives";
import { formatCompactCurrency } from "@/lib/currency";
import type { Deal, PipelineStage } from "@/lib/deals";
import { cn } from "@/lib/format";
import { sortCards, sumInReporting, type SortOrder } from "@/lib/pipeline-math";
import { DealCard } from "./deal-card";
import { useDroppable } from "@dnd-kit/core";
import { Plus } from "lucide-react";

/** One stage of the pipeline: its header with the count and total, and its cards. It is also a drop target. */
export function StageColumn({
  stage,
  deals,
  sort,
  draggingId,
  isDropTarget,
  onCardArrow,
  onCardOpen,
}: {
  stage: PipelineStage;
  deals: Deal[];
  sort: SortOrder;
  draggingId: string | null;
  isDropTarget: boolean;
  onCardArrow: (deal: Deal, direction: -1 | 1) => void;
  onCardOpen: (deal: Deal) => void;
}) {
  const { reportingCurrency, convertToReporting } = useCurrency();
  const cards = sortCards(deals, sort, convertToReporting);
  const total = sumInReporting(cards, convertToReporting);
  const { setNodeRef } = useDroppable({ id: stage.code });

  return (
    <section
      ref={setNodeRef}
      aria-label={`${stage.name}, ${cards.length} deals`}
      className={cn(
        "flex w-[264px] flex-col rounded-xl border bg-surface-muted transition-colors",
        isDropTarget && draggingId ? "border-brand bg-brand-soft/60" : "border-transparent",
      )}
    >
      <header className="px-3 pt-3 pb-2">
        <div className="flex items-center gap-2">
          <span className={cn("size-2 rounded-full", STAGE_DOT[stage.code])} aria-hidden />
          <h2 className="text-[13px] font-semibold text-ink">{stage.name}</h2>
          <span className="tnum rounded-full bg-surface px-1.5 text-[11.5px] font-medium text-ink-2 ring-1 ring-line">
            {cards.length}
          </span>
          <span className="tnum ml-auto text-[12.5px] font-semibold text-ink">
            {total == null ? "—" : formatCompactCurrency(total, reportingCurrency)}
          </span>
          <button
            onClick={() => window.dispatchEvent(new CustomEvent("nexus:create", { detail: "deal" }))}
            aria-label={`Add deal to ${stage.name}`}
            title={`Add deal · choose ${stage.name} in the form`}
            className="grid size-6 place-items-center rounded-md text-ink-3 hover:bg-surface hover:text-brand"
          >
            <Plus className="size-3.5" />
          </button>
        </div>
        <p className="tnum mt-1 text-[11.5px] text-ink-3">
          {stage.winProbability}% probability ·{" "}
          {total == null ? "—" : formatCompactCurrency((total * stage.winProbability) / 100, reportingCurrency)}{" "}
          weighted
        </p>
      </header>

      <ul className="flex min-h-24 flex-1 flex-col gap-2 p-2 pt-1">
        {cards.map((deal) => (
          <DealCard
            key={deal.id}
            deal={deal}
            dragging={draggingId === deal.id}
            onArrow={(direction) => onCardArrow(deal, direction)}
            onOpen={() => onCardOpen(deal)}
          />
        ))}
        {cards.length === 0 && (
          <li className="grid flex-1 place-items-center rounded-lg border border-dashed border-line-strong px-3 py-6 text-center text-[12.5px] text-ink-3">
            No deals in this stage
          </li>
        )}
      </ul>
    </section>
  );
}
