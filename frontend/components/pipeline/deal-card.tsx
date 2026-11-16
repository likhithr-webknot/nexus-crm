import { useCurrency } from "@/components/currency/currency-provider";
import { Avatar } from "@/components/ui/primitives";
import type { Deal } from "@/lib/deals";
import { cn, formatDay, localIso } from "@/lib/format";
import { moneyValue } from "@/lib/money";

/** One deal on the board. It can be dragged, or moved with the arrow keys. */
export function DealCard({
  deal,
  dragging,
  onDragStart,
  onDragEnd,
  onArrow,
}: {
  deal: Deal;
  dragging: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  /** -1 for the left arrow and 1 for the right. */
  onArrow: (direction: -1 | 1) => void;
}) {
  const { format } = useCurrency();
  const amount = format(moneyValue(deal.amount), deal.amount.currency);

  return (
    <li
      id={`deal-${deal.id}`}
      tabIndex={0}
      draggable
      aria-label={`${deal.company.name}, ${deal.primaryContact?.name ?? "no contact"}, ${amount}. Arrow keys move it between stages.`}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onKeyDown={(e) => {
        if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
        e.preventDefault();
        onArrow(e.key === "ArrowRight" ? 1 : -1);
      }}
      className={cn(
        "cursor-grab rounded-lg border border-line bg-surface p-3 shadow-[0_1px_2px_rgba(15,23,42,0.04)] transition-shadow hover:shadow-[0_4px_12px_-4px_rgba(15,23,42,0.16)] active:cursor-grabbing",
        dragging && "opacity-40",
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="truncate text-[11.5px] text-ink-3">{deal.name}</span>
        <span className="tnum shrink-0 text-[11.5px] text-ink-3">{formatDay(localIso(deal.updatedAt))}</span>
      </div>
      <p className="mt-2.5 truncate text-[13.5px] font-semibold text-ink">{deal.company.name}</p>
      <p className="truncate text-[12.5px] text-ink-3">{deal.primaryContact?.name ?? "No contact"}</p>
      <div className="mt-3 flex min-h-[38px] items-center justify-between gap-2 border-t border-line pt-2.5">
        <span className="tnum text-[15px] font-semibold tracking-[-0.01em] text-ink">{amount}</span>
        <span className="flex items-center gap-1.5 text-[12px] text-ink-2">
          <Avatar name={deal.owner.name} />
          {deal.owner.name.split(" ")[0]}
        </span>
      </div>
    </li>
  );
}
