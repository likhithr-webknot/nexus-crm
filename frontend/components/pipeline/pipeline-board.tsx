"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { DeleteDialog } from "@/components/ui/delete-dialog";
import { LoadFailed } from "@/components/ui/load-state";
import { Card, Page, PageHeader, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useAssignableUsers } from "@/hooks/use-people";
import type { Deal } from "@/lib/deals";
import { deleteDeal } from "@/lib/deals";
import { cn } from "@/lib/format";
import type { SortOrder } from "@/lib/pipeline-math";
import { Plus, Settings2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { DndContext, DragOverlay, PointerSensor, useSensor, useSensors } from "@dnd-kit/core";
import { AddDealDialog } from "./add-deal-dialog";
import { EditDealDialog } from "./edit-deal-dialog";
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
  const [editing, setEditing] = useState<Deal | null>(null);
  const [deleting, setDeleting] = useState<Deal | null>(null);
  // A card moved with the keyboard is re-created in its new column; this puts focus back on it.
  const refocus = useRef<string | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));

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

      <DndContext sensors={sensors} autoScroll onDragStart={(event) => setDragging(String(event.active.id))} onDragOver={(event) => setDropStage(event.over ? String(event.over.id) : null)} onDragCancel={() => { setDragging(null); setDropStage(null); }} onDragEnd={(event) => {
        const dragged = board.deals.find((deal) => deal.id === String(event.active.id));
        const target = event.over ? String(event.over.id) : null;
        if (dragged && target && board.stageByCode.has(target)) requestMove(dragged, target);
        setDragging(null);
        setDropStage(null);
      }}>
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
              onCardOpen={setEditing}
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
      <DragOverlay dropAnimation={{ duration: 160, easing: "cubic-bezier(0.22,1,0.36,1)" }}>
        {dragging && (() => { const deal = board.deals.find((item) => item.id === dragging); return deal ? <div className="w-[248px] rotate-[.3deg] scale-[1.02] rounded-lg border border-brand/30 bg-surface p-3 shadow-xl"><p className="truncate text-[11px] text-ink-3">{deal.name}</p><p className="mt-2 text-[13px] font-semibold">{deal.company.name}</p><p className="tnum mt-3 text-[14px] font-semibold">{deal.amount.amount} {deal.amount.currency}</p></div> : null; })()}
      </DragOverlay>
      </DndContext>

      <AddDealDialog open={adding} onClose={() => setAdding(false)} onAdded={board.refresh} />
      <EditDealDialog deal={editing} onClose={() => setEditing(null)} onSaved={board.refresh} onDelete={setDeleting} />
      <DeleteDialog
        target={deleting}
        title={(d) => `Delete ${d.company.name}?`}
        body="The deal is removed from the pipeline. An admin can recover it from the database if this was a mistake."
        successMessage={(d) => `${d.company.name} deleted.`}
        action={(d) => deleteDeal(d.id)}
        onClose={() => setDeleting(null)}
        onDeleted={board.refresh}
      />
      <LostDealDialog
        deal={losing}
        pending={board.move.isPending}
        onClose={() => setLosing(null)}
        onConfirm={(reason) => losing && board.move.mutate({ deal: losing, stageCode: "LOST", lostReason: reason })}
      />
    </Page>
  );
}
