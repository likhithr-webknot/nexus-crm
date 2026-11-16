import { Card } from "@/components/ui/primitives";
import { monthTitle, WEEKDAYS, type Cell, type Month } from "@/lib/calendar";
import { cn, formatLongDay, formatTime } from "@/lib/format";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { dayOf, TYPE_STYLES, type Item } from "./follow-up-item";

const navButton =
  "grid size-8 place-items-center rounded-lg border border-line bg-surface text-ink-2 transition-colors hover:border-line-strong hover:text-ink";

/** A month as a grid of days. Each day shows its follow-ups, and picking a day shows its agenda. */
export function FollowUpCalendar({
  month,
  grid,
  items,
  today,
  selected,
  onSelect,
  onToday,
  onShift,
}: {
  month: Month;
  grid: Cell[];
  items: Item[];
  today: string;
  selected: string;
  onSelect: (iso: string) => void;
  onToday: () => void;
  onShift: (by: number) => void;
}) {
  return (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-2 px-4 py-3">
        <h2 className="mr-auto text-[15px] font-semibold tracking-[-0.01em] text-ink">{monthTitle(month)}</h2>
        <button onClick={onToday} className={cn(navButton, "w-auto px-3 text-[13px] font-medium")}>
          Today
        </button>
        <button onClick={() => onShift(-1)} aria-label="Previous month" className={navButton}>
          <ChevronLeft className="size-4" aria-hidden />
        </button>
        <button onClick={() => onShift(1)} aria-label="Next month" className={navButton}>
          <ChevronRight className="size-4" aria-hidden />
        </button>
      </div>

      <div className="grid grid-cols-7 border-y border-line bg-canvas/60 text-center text-[11.5px] font-medium tracking-wide text-ink-3 uppercase">
        {WEEKDAYS.map((w) => (
          <div key={w} className="py-2">
            {w}
          </div>
        ))}
      </div>

      {/* The 1px gaps show the grid colour underneath, which draws the cell borders. */}
      <div className="grid grid-cols-7 gap-px bg-line">
        {grid.map((cell) => (
          <DayCell key={cell.iso} cell={cell} items={items.filter((f) => dayOf(f) === cell.iso)} isToday={cell.iso === today} isSelected={cell.iso === selected} onSelect={onSelect} />
        ))}
      </div>
    </Card>
  );
}

function DayCell({ cell, items, isToday, isSelected, onSelect }: { cell: Cell; items: Item[]; isToday: boolean; isSelected: boolean; onSelect: (iso: string) => void }) {
  return (
    <button
      onClick={() => onSelect(cell.iso)}
      aria-label={`${formatLongDay(cell.iso)}, ${items.length} follow-ups`}
      aria-pressed={isSelected}
      className={cn(
        "flex min-h-[68px] flex-col gap-1 p-1.5 text-left transition-colors focus-visible:relative sm:min-h-[104px] sm:p-2",
        isSelected ? "bg-brand-soft/70" : cell.inMonth ? "bg-surface hover:bg-canvas/70" : "bg-canvas/60",
      )}
    >
      <span className={cn("tnum grid size-6 place-items-center rounded-full text-[12px] font-medium", isToday ? "bg-brand text-white" : cell.inMonth ? "text-ink" : "text-ink-3")}>
        {cell.number}
      </span>
      {/* Phones get dots; wider screens get readable chips. */}
      <span className="flex gap-1 sm:hidden" aria-hidden>
        {items.slice(0, 4).map((f) => (
          <span key={f.id} className={cn("size-1.5 rounded-full", f.done ? "bg-line-strong" : TYPE_STYLES[f.type].bar)} />
        ))}
      </span>
      <span className="hidden w-full flex-col gap-1 sm:flex" aria-hidden>
        {items.slice(0, 2).map((f) => (
          <span
            key={f.id}
            className={cn("truncate rounded border-l-2 px-1.5 py-0.5 text-[11.5px]", f.done ? "border-line-strong bg-surface-muted text-ink-3 line-through" : TYPE_STYLES[f.type].chip)}
          >
            <span className="tnum font-semibold">{formatTime(f.local)}</span> {(f.about?.name ?? f.title).split(" ")[0]}
          </span>
        ))}
        {items.length > 2 && <span className="px-1.5 text-[11.5px] text-ink-3">+{items.length - 2} more</span>}
      </span>
    </button>
  );
}
