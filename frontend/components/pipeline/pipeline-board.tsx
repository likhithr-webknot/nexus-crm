"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Plus, Settings2 } from "lucide-react";
import { OWNERS, STAGES, STAGE_PROBABILITY, TODAY, type Deal, type Priority, type Stage } from "@/lib/mock-data";
import {
  Avatar,
  Badge,
  Card,
  Field,
  Page,
  PageHeader,
  SESSION_ONLY,
  STAGE_DOT,
  buttonPrimary,
  buttonSecondary,
  control,
  fieldControl,
} from "@/components/ui/primitives";
import { Dialog, DialogActions, DialogBody } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { cn, formatCompactUsd, formatDay, formatUsd, readForm } from "@/lib/format";

const PRIORITY_TONE: Record<Priority, "red" | "amber" | "neutral"> = { High: "red", Medium: "amber", Low: "neutral" };
const UNASSIGNED = "unassigned";
const isOpen = (d: Deal) => d.stage !== "Won" && d.stage !== "Lost";
const sum = (deals: Deal[]) => deals.reduce((total, d) => total + d.value, 0);

export function PipelineBoard({ deals: initial }: { deals: Deal[] }) {
  const [deals, setDeals] = useState(initial);
  const [owner, setOwner] = useState("");
  const [sort, setSort] = useState<"value" | "recent">("value");
  const [dragging, setDragging] = useState<string | null>(null);
  const [dropStage, setDropStage] = useState<Stage | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const [adding, setAdding] = useState(false);
  // A card moved with the keyboard is re-created in its new column; this puts focus back on it.
  const refocus = useRef<string | null>(null);

  useEffect(() => {
    if (!refocus.current) return;
    document.getElementById(`deal-${refocus.current}`)?.focus();
    refocus.current = null;
  });

  const move = (id: string, stage: Stage) => {
    const deal = deals.find((d) => d.id === id);
    if (!deal || deal.stage === stage) return;
    setDeals(deals.map((d) => (d.id === id ? { ...d, stage } : d)));
    setAnnouncement(`${deal.company} moved to ${stage}.`);
  };

  const owners = [...new Set(initial.flatMap((d) => (d.owner ? [d.owner] : [])))].sort();
  const visible = deals.filter((d) => !owner || (owner === UNASSIGNED ? d.owner === null : d.owner === owner));
  const open = visible.filter(isOpen);
  const won = visible.filter((d) => d.stage === "Won");
  const closed = won.length + visible.filter((d) => d.stage === "Lost").length;
  const weighted = open.reduce((total, d) => total + (d.value * STAGE_PROBABILITY[d.stage]) / 100, 0);

  const figures = [
    { label: "Open pipeline", value: formatUsd(sum(open)), note: `${open.length} deals` },
    { label: "Weighted forecast", value: formatUsd(Math.round(weighted)), note: "Value × chance to win" },
    { label: "Won", value: formatUsd(sum(won)), note: `${won.length} deals` },
    { label: "Win rate", value: closed ? `${Math.round((won.length / closed) * 100)}%` : "–", note: "Of closed deals" },
  ];

  return (
    <Page>
      <PageHeader title="Sales Pipeline" subtitle="Track every deal from first contact to close.">
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
        <dl className="grid flex-1 grid-cols-2 gap-x-8 gap-y-4 sm:flex sm:flex-none">
          {figures.map((f) => (
            <div key={f.label}>
              <dt className="text-[12px] font-medium text-ink-3">{f.label}</dt>
              <dd className="tnum mt-1 text-[20px] leading-none font-semibold tracking-[-0.02em] text-ink">{f.value}</dd>
              <dd className="mt-1.5 text-[12px] text-ink-3">{f.note}</dd>
            </div>
          ))}
        </dl>
        <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
          <select aria-label="Owner" value={owner} onChange={(e) => setOwner(e.target.value)} className={cn(control, "px-2.5")}>
            <option value="">All owners</option>
            <option value={UNASSIGNED}>Unassigned</option>
            {owners.map((o) => (
              <option key={o}>{o}</option>
            ))}
          </select>
          <select
            aria-label="Sort cards"
            value={sort}
            onChange={(e) => setSort(e.target.value as typeof sort)}
            className={cn(control, "px-2.5")}
          >
            <option value="value">Highest value first</option>
            <option value="recent">Newest first</option>
          </select>
        </div>
      </Card>

      <p className="text-[12.5px] text-ink-3">
        Drag a card to another stage, or focus it and press <kbd className="font-mono">←</kbd>{" "}
        <kbd className="font-mono">→</kbd>.
      </p>
      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>

      {/* Negative margins let the board scroll to the edge of the screen. */}
      <div className="-mx-4 overflow-x-auto px-4 pb-3 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
        <div className="flex min-w-max gap-3">
          {STAGES.map((stage, index) => {
            const cards = visible
              .filter((d) => d.stage === stage)
              .sort((a, b) => (sort === "value" ? b.value - a.value : b.updatedAt.localeCompare(a.updatedAt)));
            return (
              <section
                key={stage}
                aria-label={`${stage}, ${cards.length} deals`}
                onDragOver={(e) => {
                  e.preventDefault();
                  setDropStage(stage);
                }}
                onDragLeave={(e) => {
                  if (!e.currentTarget.contains(e.relatedTarget as Node)) setDropStage(null);
                }}
                onDrop={() => {
                  if (dragging) move(dragging, stage);
                  setDragging(null);
                  setDropStage(null);
                }}
                className={cn(
                  "flex w-[264px] flex-col rounded-xl border bg-slate-100/70 transition-colors",
                  dropStage === stage && dragging ? "border-brand bg-brand-soft/60" : "border-transparent",
                )}
              >
                <header className="px-3 pt-3 pb-2">
                  <div className="flex items-center gap-2">
                    <span className={cn("size-2 rounded-full", STAGE_DOT[stage])} aria-hidden />
                    <h2 className="text-[13px] font-semibold text-ink">{stage}</h2>
                    <span className="tnum rounded-full bg-surface px-1.5 text-[11.5px] font-medium text-ink-2 ring-1 ring-line">
                      {cards.length}
                    </span>
                    <span className="tnum ml-auto text-[12.5px] font-semibold text-ink">{formatCompactUsd(sum(cards))}</span>
                  </div>
                  <p className="mt-1 text-[11.5px] text-ink-3">{STAGE_PROBABILITY[stage]}% chance to win</p>
                </header>

                <ul className="flex min-h-24 flex-1 flex-col gap-2 p-2 pt-1">
                  {cards.map((deal) => (
                    <li
                      key={deal.id}
                      id={`deal-${deal.id}`}
                      tabIndex={0}
                      draggable
                      aria-label={`${deal.company}, ${deal.contact}, ${formatUsd(deal.value)}, ${deal.priority} priority. Arrow keys move it between stages.`}
                      onDragStart={() => setDragging(deal.id)}
                      onDragEnd={() => {
                        setDragging(null);
                        setDropStage(null);
                      }}
                      onKeyDown={(e) => {
                        const target = STAGES[index + (e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0)];
                        if (!target || target === stage) return;
                        e.preventDefault();
                        refocus.current = deal.id;
                        move(deal.id, target);
                      }}
                      className={cn(
                        "cursor-grab rounded-lg border border-line bg-surface p-3 shadow-[0_1px_2px_rgba(15,23,42,0.04)] transition-shadow hover:shadow-[0_4px_12px_-4px_rgba(15,23,42,0.16)] active:cursor-grabbing",
                        dragging === deal.id && "opacity-40",
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <Badge tone={PRIORITY_TONE[deal.priority]}>{deal.priority}</Badge>
                        <span className="tnum text-[11.5px] text-ink-3">{formatDay(deal.updatedAt)}</span>
                      </div>
                      <p className="mt-2.5 truncate text-[13.5px] font-semibold text-ink">{deal.company}</p>
                      <p className="truncate text-[12.5px] text-ink-3">{deal.contact}</p>
                      <div className="mt-3 flex min-h-[38px] items-center justify-between gap-2 border-t border-line pt-2.5">
                        <span className="tnum text-[15px] font-semibold tracking-[-0.01em] text-ink">{formatUsd(deal.value)}</span>
                        {deal.owner ? (
                          <span className="flex items-center gap-1.5 text-[12px] text-ink-2">
                            <Avatar name={deal.owner} />
                            {deal.owner.split(" ")[0]}
                          </span>
                        ) : (
                          <span className="text-[12px] text-ink-3">Unassigned</span>
                        )}
                      </div>
                    </li>
                  ))}
                  {cards.length === 0 && (
                    <li className="grid flex-1 place-items-center rounded-lg border border-dashed border-line-strong px-3 py-6 text-center text-[12.5px] text-ink-3">
                      No deals in this stage
                    </li>
                  )}
                </ul>
              </section>
            );
          })}
        </div>
      </div>

      <AddDealDialog
        open={adding}
        onClose={() => setAdding(false)}
        onAdd={(deal) => {
          setDeals((all) => [...all, { ...deal, id: `D-${Date.now()}` }]);
          toast(`${deal.company} added to ${deal.stage}. ${SESSION_ONLY}`);
        }}
      />
    </Page>
  );
}

function AddDealDialog({ open, onClose, onAdd }: { open: boolean; onClose: () => void; onAdd: (deal: Omit<Deal, "id">) => void }) {
  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    onAdd({
      company: f.company,
      contact: f.contact,
      value: Number(f.value),
      stage: f.stage as Stage,
      priority: f.priority as Priority,
      owner: f.owner || null,
      updatedAt: TODAY,
    });
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} title="Add deal" description="A deal is one sale you are working on with a company.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Company">
            <input name="company" required maxLength={120} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Contact name">
            <input name="contact" required maxLength={120} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Value (USD)">
            <input name="value" type="number" required min={1} inputMode="numeric" className={fieldControl} />
          </Field>
          <Field label="Stage">
            <select name="stage" className={fieldControl}>
              {STAGES.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </Field>
          <Field label="Priority">
            <select name="priority" defaultValue="Medium" className={fieldControl}>
              <option>High</option>
              <option>Medium</option>
              <option>Low</option>
            </select>
          </Field>
          <Field label="Owner">
            <select name="owner" className={fieldControl}>
              <option value="">Unassigned</option>
              {OWNERS.map((o) => (
                <option key={o}>{o}</option>
              ))}
            </select>
          </Field>
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Add deal" />
      </form>
    </Dialog>
  );
}
