"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Settings2, WifiOff } from "lucide-react";
import {
  Avatar,
  Card,
  EmptyState,
  Field,
  Page,
  PageHeader,
  STAGE_DOT,
  buttonPrimary,
  buttonSecondary,
  control,
  fieldControl,
} from "@/components/ui/primitives";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { cn, formatDay, localIso, readForm } from "@/lib/format";
import { currencyMinorUnits, decimalPlaces, formatCompactCurrency } from "@/lib/currency";
import { useCurrency } from "@/components/currency/currency-provider";
import { errorMessage } from "@/lib/api";
import { createDeal, listStages, moveDeal, pipelineBoard, type Deal } from "@/lib/deals";
import { createCompany, listCompanies, listContacts } from "@/lib/contacts";
import { moneyValue } from "@/lib/money";
import { listAssignable } from "@/lib/users";
import { useSession } from "@/components/auth/session";

const UNASSIGNED = "unassigned";

export function PipelineBoard() {
  const { reportingCurrency, convertToReporting, format } = useCurrency();
  const queryClient = useQueryClient();
  const [owner, setOwner] = useState("");
  const [sort, setSort] = useState<"value" | "recent">("value");
  const [dragging, setDragging] = useState<string | null>(null);
  const [dropStage, setDropStage] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const [adding, setAdding] = useState(false);
  // A deal that is being marked lost waits here until the reason is given.
  const [losing, setLosing] = useState<Deal | null>(null);
  // A card moved with the keyboard is re-created in its new column; this puts focus back on it.
  const refocus = useRef<string | null>(null);

  const stagesQuery = useQuery({ queryKey: ["pipeline", "stages"], queryFn: listStages, staleTime: Infinity });
  const boardQuery = useQuery({ queryKey: ["pipeline", "board"], queryFn: () => pipelineBoard() });
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable });
  const stages = stagesQuery.data ?? [];
  const deals = boardQuery.data ?? [];
  const stageByCode = new Map(stages.map((s) => [s.code, s]));

  useEffect(() => {
    if (!refocus.current) return;
    document.getElementById(`deal-${refocus.current}`)?.focus();
    refocus.current = null;
  });

  const refresh = () => {
    for (const key of ["pipeline", "deals", "dashboard", "overview", "activities"]) void queryClient.invalidateQueries({ queryKey: [key] });
  };

  const move = useMutation({
    mutationFn: (v: { deal: Deal; stageCode: string; lostReason?: string }) =>
      moveDeal(v.deal.id, { stageCode: v.stageCode, version: v.deal.version, lostReason: v.lostReason }),
    onSuccess: (deal, v) => {
      setAnnouncement(`${deal.company.name} moved to ${stageByCode.get(v.stageCode)?.name ?? v.stageCode}.`);
      setLosing(null);
      refresh();
    },
    onError: (e) => {
      toast(errorMessage(e, "The deal could not be moved."));
      // Someone else may have changed the deal, so show what it looks like now.
      refresh();
    },
  });

  const requestMove = (id: string, stageCode: string) => {
    const deal = deals.find((d) => d.id === id);
    if (!deal || deal.stageCode === stageCode) return;
    if (deal.closedAt) return toast("A won or lost deal can't move to another stage.");
    if (stageByCode.get(stageCode)?.kind === "LOST") return setLosing(deal);
    move.mutate({ deal, stageCode });
  };

  const visible = deals.filter((d) => !owner || (owner === UNASSIGNED ? false : d.owner.id === owner));
  const isOpen = (d: Deal) => !d.closedAt;
  const open = visible.filter(isOpen);
  const won = visible.filter((d) => d.stageCode === "WON");
  const closed = won.length + visible.filter((d) => d.stageCode === "LOST").length;
  const reportSum = (items: Deal[]) => {
    let total = 0;
    for (const deal of items) {
      const converted = convertToReporting(moneyValue(deal.amount), deal.amount.currency);
      if (converted == null) return null;
      total += converted;
    }
    return total;
  };
  const weighted = open.reduce((total, deal) => {
    const converted = convertToReporting(moneyValue(deal.amount), deal.amount.currency);
    const chance = stageByCode.get(deal.stageCode)?.winProbability ?? 0;
    return total == null || converted == null ? null : total + (converted * chance) / 100;
  }, 0 as number | null);

  const figures = [
    { label: "Open pipeline", value: reportSum(open) == null ? "—" : format(reportSum(open)!, reportingCurrency), note: `${open.length} deals` },
    { label: "Weighted forecast", value: weighted == null ? "—" : format(Math.round(weighted), reportingCurrency), note: "Value × chance to win" },
    { label: "Won", value: reportSum(won) == null ? "—" : format(reportSum(won)!, reportingCurrency), note: `${won.length} deals, last 90 days` },
    { label: "Win rate", value: closed ? `${Math.round((won.length / closed) * 100)}%` : "–", note: "Of closed deals" },
  ];

  if (boardQuery.isError || stagesQuery.isError) {
    return (
      <Page>
        <PageHeader title="Sales Pipeline" subtitle="Track every deal from first contact to close." />
        <Card>
          <EmptyState icon={WifiOff} title="Couldn't load the pipeline" body="Check your connection, then try again.">
            <button
              onClick={() => {
                void boardQuery.refetch();
                void stagesQuery.refetch();
              }}
              className={buttonSecondary}
            >
              Try again
            </button>
          </EmptyState>
        </Card>
      </Page>
    );
  }

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
            {(people.data ?? []).map((u) => (
              <option key={u.id} value={u.id}>
                {u.displayName}
              </option>
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
          {stages.map((stageInfo, index) => {
            const stage = stageInfo.code;
            const cards = visible
              .filter((d) => d.stageCode === stage)
              .sort((a, b) => {
                if (sort === "recent") return b.updatedAt.localeCompare(a.updatedAt);
                const aValue = convertToReporting(moneyValue(a.amount), a.amount.currency);
                const bValue = convertToReporting(moneyValue(b.amount), b.amount.currency);
                return aValue == null || bValue == null ? b.updatedAt.localeCompare(a.updatedAt) : bValue - aValue;
              });
            const stageTotal = reportSum(cards);
            return (
              <section
                key={stage}
                aria-label={`${stageInfo.name}, ${cards.length} deals`}
                onDragOver={(e) => {
                  e.preventDefault();
                  setDropStage(stage);
                }}
                onDragLeave={(e) => {
                  if (!e.currentTarget.contains(e.relatedTarget as Node)) setDropStage(null);
                }}
                onDrop={() => {
                  if (dragging) requestMove(dragging, stage);
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
                    <h2 className="text-[13px] font-semibold text-ink">{stageInfo.name}</h2>
                    <span className="tnum rounded-full bg-surface px-1.5 text-[11.5px] font-medium text-ink-2 ring-1 ring-line">
                      {cards.length}
                    </span>
                    <span className="tnum ml-auto text-[12.5px] font-semibold text-ink">
                      {stageTotal == null ? "—" : formatCompactCurrency(stageTotal, reportingCurrency)}
                    </span>
                  </div>
                  <p className="mt-1 text-[11.5px] text-ink-3">{stageInfo.winProbability}% chance to win</p>
                </header>

                <ul className="flex min-h-24 flex-1 flex-col gap-2 p-2 pt-1">
                  {cards.map((deal) => (
                    <li
                      key={deal.id}
                      id={`deal-${deal.id}`}
                      tabIndex={0}
                      draggable
                      aria-label={`${deal.company.name}, ${deal.primaryContact?.name ?? "no contact"}, ${format(moneyValue(deal.amount), deal.amount.currency)}. Arrow keys move it between stages.`}
                      onDragStart={() => setDragging(deal.id)}
                      onDragEnd={() => {
                        setDragging(null);
                        setDropStage(null);
                      }}
                      onKeyDown={(e) => {
                        const target = stages[index + (e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0)]?.code;
                        if (!target || target === stage) return;
                        e.preventDefault();
                        refocus.current = deal.id;
                        requestMove(deal.id, target);
                      }}
                      className={cn(
                        "cursor-grab rounded-lg border border-line bg-surface p-3 shadow-[0_1px_2px_rgba(15,23,42,0.04)] transition-shadow hover:shadow-[0_4px_12px_-4px_rgba(15,23,42,0.16)] active:cursor-grabbing",
                        dragging === deal.id && "opacity-40",
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate text-[11.5px] text-ink-3">{deal.name}</span>
                        <span className="tnum shrink-0 text-[11.5px] text-ink-3">{formatDay(localIso(deal.updatedAt))}</span>
                      </div>
                      <p className="mt-2.5 truncate text-[13.5px] font-semibold text-ink">{deal.company.name}</p>
                      <p className="truncate text-[12.5px] text-ink-3">{deal.primaryContact?.name ?? "No contact"}</p>
                      <div className="mt-3 flex min-h-[38px] items-center justify-between gap-2 border-t border-line pt-2.5">
                        <span className="tnum text-[15px] font-semibold tracking-[-0.01em] text-ink">
                          {format(moneyValue(deal.amount), deal.amount.currency)}
                        </span>
                        <span className="flex items-center gap-1.5 text-[12px] text-ink-2">
                          <Avatar name={deal.owner.name} />
                          {deal.owner.name.split(" ")[0]}
                        </span>
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

      <AddDealDialog open={adding} onClose={() => setAdding(false)} onAdded={refresh} />
      <LostDialog
        deal={losing}
        pending={move.isPending}
        onClose={() => setLosing(null)}
        onConfirm={(reason) => losing && move.mutate({ deal: losing, stageCode: "LOST", lostReason: reason })}
      />
    </Page>
  );
}

function LostDialog({
  deal,
  pending,
  onClose,
  onConfirm,
}: {
  deal: Deal | null;
  pending: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  return (
    <Dialog open={deal !== null} onClose={onClose} title="Mark deal as lost" description={deal ? `${deal.company.name} · ${deal.name}` : undefined} size="sm">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          onConfirm(readForm(e.currentTarget).reason);
        }}
      >
        <DialogBody>
          <Field label="Why was it lost?">
            <input name="reason" required maxLength={500} autoComplete="off" placeholder="Went with a competitor on price" className={fieldControl} />
          </Field>
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Mark as lost" danger pending={pending} />
      </form>
    </Dialog>
  );
}

function AddDealDialog({ open, onClose, onAdded }: { open: boolean; onClose: () => void; onAdded: () => void }) {
  const { reportingCurrency, currencies } = useCurrency();
  const session = useSession();
  const [error, setError] = useState<string | null>(null);
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable, enabled: open });
  const companies = useQuery({ queryKey: ["companies", "all"], queryFn: () => listCompanies({ size: 100 }), enabled: open });
  const contacts = useQuery({ queryKey: ["contacts", ""], queryFn: () => listContacts({ size: 100 }), enabled: open });
  const stages = useQuery({ queryKey: ["pipeline", "stages"], queryFn: listStages, staleTime: Infinity });

  const add = useMutation({
    mutationFn: async (f: Record<string, string>) => {
      // The deal belongs to a company: use the one with this name, or create it.
      const existing = companies.data?.items.find((c) => c.name.toLowerCase() === f.company.toLowerCase());
      const companyId = existing?.id ?? (await createCompany({ name: f.company })).id;
      return createDeal({
        name: f.name || f.company,
        companyId,
        primaryContactId: f.contact || undefined,
        ownerId: f.owner || undefined,
        stageCode: f.stage,
        amount: { amount: f.value, currency: f.currency },
      });
    },
    onSuccess: (deal) => {
      toast(`${deal.company.name} added to the pipeline.`);
      onAdded();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    if (decimalPlaces(f.value ?? "") > currencyMinorUnits(f.currency)) {
      const input = e.currentTarget.elements.namedItem("value");
      if (input instanceof HTMLInputElement) {
        input.setCustomValidity(`${f.currency} allows up to ${currencyMinorUnits(f.currency)} decimal places.`);
        input.reportValidity();
      }
      return;
    }
    setError(null);
    add.mutate(f);
  };

  return (
    <Dialog open={open} onClose={onClose} title="Add deal" description="A deal is one sale you are working on with a company.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Company">
            <input name="company" list="deal-companies" required maxLength={160} autoComplete="off" className={fieldControl} />
            <datalist id="deal-companies">
              {companies.data?.items.map((c) => (
                <option key={c.id} value={c.name} />
              ))}
            </datalist>
          </Field>
          <Field label="Deal name" optional>
            <input name="name" maxLength={160} autoComplete="off" placeholder="Defaults to the company name" className={fieldControl} />
          </Field>
          <Field label="Contact" optional>
            <select name="contact" defaultValue="" className={fieldControl}>
              <option value="">No contact</option>
              {contacts.data?.items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.company ? `, ${c.company.name}` : ""}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Value">
            <input
              name="value"
              type="number"
              required
              min={0}
              step="any"
              inputMode="decimal"
              onChange={(event) => event.currentTarget.setCustomValidity("")}
              className={fieldControl}
            />
          </Field>
          <Field label="Currency">
            <select name="currency" defaultValue={reportingCurrency} className={fieldControl}>
              {currencies.map(({ code, name }) => (
                <option key={code} value={code}>
                  {code} — {name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Stage">
            <select name="stage" defaultValue="QUALIFIED" className={fieldControl}>
              {stages.data
                ?.filter((s) => s.kind === "OPEN")
                .map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Owner">
            <select name="owner" defaultValue={session.id} className={fieldControl}>
              {(people.data ?? [{ id: session.id, displayName: session.displayName }]).map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
          {error && (
            <div className="sm:col-span-2">
              <FormError>{error}</FormError>
            </div>
          )}
        </DialogBody>
        <DialogActions onCancel={onClose} submitLabel="Add deal" pending={add.isPending} />
      </form>
    </Dialog>
  );
}
