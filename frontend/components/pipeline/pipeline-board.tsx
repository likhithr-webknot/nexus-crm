"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { LoadFailed } from "@/components/ui/load-state";
import { Card, Page, PageHeader, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useAssignableUsers } from "@/hooks/use-people";
import type { Deal } from "@/lib/deals";
import { cn } from "@/lib/format";
import type { SortOrder } from "@/lib/pipeline-math";
import { Plus, Settings2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AddDealDialog } from "./add-deal-dialog";
import { LostDealDialog } from "./lost-deal-dialog";
import { PipelineFigures } from "./pipeline-figures";
import { StageColumn } from "./stage-column";
import { useDealBoard } from "./use-deal-board";

/** The pipeline page. Owns only what is on screen right now: the filters, the drag, and which dialog is open. */
export function PipelineBoard() {
  const { reportingCurrency } = useCurrency();
  const people = useAssignableUsers();
  const [owner, setOwner] = useState("");
  const [sort, setSort] = useState<SortOrder>("value");
  const [dragging, setDragging] = useState<string | null>(null);
  const [dropStage, setDropStage] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const [adding, setAdding] = useState(false);
  // A deal that is being marked lost waits here until the reason is given.
  const [losing, setLosing] = useState<Deal | null>(null);
  // A card moved with the keyboard is re-created in its new column; this puts focus back on it.
  const refocus = useRef<string | null>(null);

  const board = useDealBoard((message) => {
    setAnnouncement(message);
    setLosing(null);
  });

  useEffect(() => {
    if (!refocus.current) return;
    document.getElementById(`deal-${refocus.current}`)?.focus();
    refocus.current = null;
  });

  const requestMove = (deal: Deal, stageCode: string) => {
    if (deal.stageCode === stageCode) return;
    if (deal.closedAt) return toast("A won or lost deal can't move to another stage.");
    if (board.stageByCode.get(stageCode)?.kind === "LOST") return setLosing(deal);
    board.move.mutate({ deal, stageCode });
  };

  if (board.failed) {
    return (
      <Page>
        <PageHeader title="Sales Pipeline" subtitle="Track every deal from first contact to close." />
        <Card>
          <LoadFailed what="the pipeline" onRetry={board.retry} />
        </Card>
      </Page>
    );
  }

  const visible = board.deals.filter((d) => !owner || d.owner.id === owner);

  return (
    <Page>
      <PageHeader title="Sales Pipeline" subtitle={`Track every deal from first contact to close. Totals in ${reportingCurrency}.`}>
        <Link href="/settings?section=stages" className={buttonSecondary}>
          <Settings2 className="size-4" aria-hidden />
          Stage settings
        </Link>
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          Add deal
        </button>
      </PageHeader>

      <Card className="flex flex-wrap items-center gap-x-8 gap-y-4 px-5 py-4">
        <PipelineFigures deals={visible} stages={board.stageByCode} />
        <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
          <select aria-label="Owner" value={owner} onChange={(e) => setOwner(e.target.value)} className={cn(control, "px-2.5")}>
            <option value="">All owners</option>
            {(people.data ?? []).map((u) => (
              <option key={u.id} value={u.id}>
                {u.displayName}
              </option>
            ))}
          </select>
          <select aria-label="Sort cards" value={sort} onChange={(e) => setSort(e.target.value as SortOrder)} className={cn(control, "px-2.5")}>
            <option value="value">Highest value first</option>
            <option value="recent">Newest first</option>
          </select>
        </div>
      </Card>

      <p className="text-[12.5px] text-ink-3">
        Drag a card to another stage, or focus it and press <kbd className="font-mono">←</kbd> <kbd className="font-mono">→</kbd>.
      </p>
      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>

      {/* Negative margins let the board scroll to the edge of the screen. */}
      <div className="-mx-4 overflow-x-auto px-4 pb-3 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
        <div className="flex min-w-max gap-3">
          {board.stages.map((stage, index) => (
            <StageColumn
              key={stage.code}
              stage={stage}
              deals={visible.filter((d) => d.stageCode === stage.code)}
              sort={sort}
              draggingId={dragging}
              isDropTarget={dropStage === stage.code}
              onDragOver={() => setDropStage(stage.code)}
              onDragLeave={() => setDropStage(null)}
              onDrop={() => {
                const dragged = board.deals.find((d) => d.id === dragging);
                if (dragged) requestMove(dragged, stage.code);
                setDragging(null);
                setDropStage(null);
              }}
              onCardDragStart={(deal) => setDragging(deal.id)}
              onCardDragEnd={() => {
                setDragging(null);
                setDropStage(null);
              }}
              onCardArrow={(deal, direction) => {
                const target = board.stages[index + direction];
                if (!target) return;
                refocus.current = deal.id;
                requestMove(deal, target.code);
              }}
            />
          ))}
        </div>
      </div>

      <AddDealDialog open={adding} onClose={() => setAdding(false)} onAdded={board.refresh} />
      <LostDealDialog
        deal={losing}
        pending={board.move.isPending}
        onClose={() => setLosing(null)}
        onConfirm={(reason) => losing && board.move.mutate({ deal: losing, stageCode: "LOST", lostReason: reason })}
      />
    </Page>
  );
}
