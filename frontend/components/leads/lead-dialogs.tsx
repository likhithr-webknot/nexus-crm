"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { Field, StageBadge, buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useSession } from "@/components/auth/session";
import { useCurrency } from "@/components/currency/currency-provider";
import { ApiError, errorMessage } from "@/lib/api";
import { downloadCsv, parseCsv } from "@/lib/csv";
import { currencyMinorUnits, decimalPlaces } from "@/lib/currency";
import { readForm, splitName } from "@/lib/format";
import {
  assignLead,
  changeLeadStatus,
  convertLead,
  createLead,
  importLeads,
  type ImportRow,
  type Lead,
} from "@/lib/leads";
import { SOURCES, SOURCE_LABELS, type LeadSource, type LeadStatus } from "@/lib/stages";
import { listAssignable } from "@/lib/users";

const EMAIL = /^\S+@\S+\.\S+$/;

/** Reloads everything that shows leads, deals or the numbers built from them. */
function useRefreshLeads() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all(
      ["leads", "lead-summary", "deals", "pipeline", "dashboard", "overview", "activities"].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    );
}

export function AddLeadDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { reportingCurrency, currencies } = useCurrency();
  const session = useSession();
  const refresh = useRefreshLeads();
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable, enabled: open });
  const [error, setError] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: createLead,
    onSuccess: (lead) => {
      toast(`${lead.name} added.`);
      void refresh();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const close = () => {
    setError(null);
    onClose();
  };

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    const currency = f.currency || reportingCurrency;
    if (f.value && decimalPlaces(f.value) > currencyMinorUnits(currency)) {
      const input = e.currentTarget.elements.namedItem("value");
      if (input instanceof HTMLInputElement) {
        input.setCustomValidity(`${currency} allows up to ${currencyMinorUnits(currency)} decimal places.`);
        input.reportValidity();
      }
      return;
    }
    setError(null);
    add.mutate({
      ...splitName(f.name),
      email: f.email,
      phone: f.phone || undefined,
      companyName: f.company || undefined,
      source: f.source as LeadSource,
      unassigned: f.owner === "unassigned",
      ownerId: f.owner && f.owner !== "unassigned" ? f.owner : undefined,
      estimatedValue: f.value ? { amount: f.value, currency } : undefined,
    });
  };

  return (
    <Dialog open={open} onClose={close} title="Add lead" description="New leads start in the New stage.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Full name">
            <input name="name" required maxLength={240} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Company" optional>
            <input name="company" maxLength={160} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Email">
            <input name="email" type="email" required maxLength={254} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Phone" optional>
            <input name="phone" type="tel" maxLength={40} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Source">
            <select name="source" defaultValue="MANUAL" className={fieldControl}>
              {SOURCES.filter((s) => s !== "IMPORT").map((s) => (
                <option key={s} value={s}>
                  {SOURCE_LABELS[s]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Assigned to">
            <select name="owner" defaultValue={session.id} className={fieldControl}>
              <option value="unassigned">Unassigned</option>
              {(people.data ?? [{ id: session.id, displayName: session.displayName }]).map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Estimated value" optional>
            <input
              name="value"
              type="number"
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
          {error && (
            <div className="sm:col-span-2">
              <FormError>{error}</FormError>
            </div>
          )}
        </DialogBody>
        <DialogActions onCancel={close} submitLabel="Add lead" pending={add.isPending} />
      </form>
    </Dialog>
  );
}

const MAX_FILE_BYTES = 1_000_000;

type Parsed = { rows: ImportRow[]; missing: number; badEmail: number; repeated: number };

/** Turns CSV text into import rows. Rows without a name or a valid email are counted, not kept. The server checks the rest. */
function readRows(text: string, defaultCurrency: string): Parsed | string {
  const [header, ...lines] = parseCsv(text);
  if (!header) return "The file is empty.";
  const column = (...names: string[]) => header.findIndex((h) => names.includes(h.trim().toLowerCase()));
  const at = {
    name: column("name", "lead name"),
    email: column("email"),
    company: column("company"),
    phone: column("phone"),
    source: column("source"),
    value: column("value", "amount", "estimated value"),
    currency: column("currency"),
  };
  if (at.name < 0 || at.email < 0) return 'The first row must name the columns, and include "Name" and "Email".';

  const sourceByLabel = new Map(SOURCES.map((s) => [SOURCE_LABELS[s].toLowerCase(), s]));
  const seen = new Set<string>();
  const result: Parsed = { rows: [], missing: 0, badEmail: 0, repeated: 0 };
  for (const line of lines) {
    // Our own export puts a quote before cells a spreadsheet could run as a formula; take it off again.
    const cell = (i: number) => (i < 0 ? "" : (line[i] ?? "").trim().replace(/^'(?=[=+\-@])/, ""));
    const name = cell(at.name);
    const email = cell(at.email).toLowerCase();
    if (!name || !email) result.missing++;
    else if (!EMAIL.test(email)) result.badEmail++;
    else if (seen.has(email)) result.repeated++;
    else {
      seen.add(email);
      result.rows.push({
        name,
        email,
        company: cell(at.company),
        phone: cell(at.phone),
        source: sourceByLabel.get(cell(at.source).toLowerCase()) ?? "IMPORT",
        value: cell(at.value),
        currency: cell(at.currency) || defaultCurrency,
      });
    }
  }
  return result;
}

export function ImportLeadsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { reportingCurrency } = useCurrency();
  const refresh = useRefreshLeads();
  const [parsed, setParsed] = useState<Parsed | string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setParsed(null);
    setError(null);
    onClose();
  };

  const upload = useMutation({
    mutationFn: importLeads,
    onSuccess: (result) => {
      toast(
        result.errors.length
          ? `${result.imported} leads imported. ${result.errors.length} rows were skipped.`
          : `${result.imported} leads imported.`,
      );
      void refresh();
      close();
    },
    onError: (e) => setError(e instanceof ApiError && e.status === 413 ? "That file is too large." : errorMessage(e)),
  });

  const pick = async (file: File | undefined) => {
    setError(null);
    if (!file) return setParsed(null);
    if (file.size > MAX_FILE_BYTES) return setParsed("That file is larger than 1 MB. Split it into smaller files.");
    setParsed(readRows(await file.text(), reportingCurrency));
  };

  const result = typeof parsed === "object" ? parsed : null;
  const skipped = result
    ? [
        [result.repeated, "repeat an email in this file"],
        [result.missing, "have no name or email"],
        [result.badEmail, "have an email that isn't valid"],
      ].filter(([count]) => count)
    : [];

  return (
    <Dialog
      open={open}
      onClose={close}
      title="Import leads"
      description="Upload a CSV file. Imported leads arrive unassigned, in the New stage."
    >
      <DialogBody>
        <Field label="CSV file" hint="Needs Name and Email columns. Company, Phone, Source, Estimated value and Currency are optional. Up to 1 MB.">
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => pick(e.target.files?.[0])}
            className="mt-1.5 block w-full text-[13px] font-normal text-ink-2 file:mr-3 file:h-9 file:cursor-pointer file:rounded-lg file:border file:border-solid file:border-line file:bg-surface file:px-3 file:text-[13px] file:font-medium file:text-ink-2 hover:file:border-line-strong"
          />
        </Field>
        <button
          type="button"
          onClick={() =>
            downloadCsv("leads-template.csv", ["Name", "Company", "Email", "Phone", "Source", "Estimated value", "Currency"], [
              ["Jane Doe", "Acme Inc.", "jane@acme.example", "+1 (415) 555-0100", "Referral", "15000", reportingCurrency],
            ])
          }
          className="justify-self-start text-[13px] font-medium text-brand hover:underline"
        >
          Download a template
        </button>

        {typeof parsed === "string" && <FormError>{parsed}</FormError>}
        {error && <FormError>{error}</FormError>}
        {result && (
          <div role="status" className="rounded-lg bg-canvas px-3.5 py-3 text-[13px] text-ink-2 ring-1 ring-line ring-inset">
            <p className="font-medium text-ink">
              {result.rows.length === 1 ? "1 row is" : `${result.rows.length} rows are`} ready to send.
            </p>
            <p className="mt-0.5">Leads whose email is already used by an open lead are skipped when you import.</p>
            {skipped.length > 0 && (
              <ul className="mt-1.5 list-disc space-y-0.5 pl-4">
                {skipped.map(([count, reason]) => (
                  <li key={reason}>
                    {count} skipped: {reason}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </DialogBody>
      <DialogActions
        onCancel={close}
        disabled={!result?.rows.length}
        pending={upload.isPending}
        submitLabel={result?.rows.length ? `Import ${result.rows.length}` : "Import"}
        onSubmit={() => result && upload.mutate(result.rows)}
      />
    </Dialog>
  );
}

const NEXT_STATUSES: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED"];

/** Everything you can do with one lead: change its status, give it to someone, or turn it into a deal. */
export function LeadActionsDialog({ lead, onClose }: { lead: Lead | null; onClose: () => void }) {
  const session = useSession();
  const refresh = useRefreshLeads();
  const people = useQuery({ queryKey: ["assignable"], queryFn: listAssignable, enabled: lead !== null });
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [disqualifying, setDisqualifying] = useState(false);
  const close = () => {
    setError(null);
    setReason("");
    setDisqualifying(false);
    onClose();
  };

  const run = useMutation({
    mutationFn: (action: () => Promise<unknown>) => action(),
    onSuccess: () => {
      void refresh();
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  if (!lead) return null;
  const open = lead.status === "NEW" || lead.status === "CONTACTED" || lead.status === "QUALIFIED";
  const canAssign = session.role !== "REP";

  return (
    <Dialog open onClose={close} title={lead.name} description={lead.companyName ?? undefined}>
      <DialogBody>
        <div className="flex items-center gap-2 text-[13px] text-ink-2">
          Status: <StageBadge stage={lead.status} />
          {lead.disqualifyReason && <span className="text-ink-3">· {lead.disqualifyReason}</span>}
        </div>

        {open && (
          <Field label="Move to">
            <div className="mt-1.5 flex flex-wrap gap-2">
              {NEXT_STATUSES.filter((s) => s !== lead.status).map((s) => (
                <button
                  key={s}
                  type="button"
                  className={buttonSecondary}
                  disabled={run.isPending}
                  onClick={() => run.mutate(() => changeLeadStatus(lead.id, s))}
                >
                  <StageBadge stage={s} />
                </button>
              ))}
              <button type="button" className={buttonSecondary} onClick={() => setDisqualifying(true)}>
                Disqualify…
              </button>
            </div>
          </Field>
        )}
        {disqualifying && (
          <Field label="Why is this lead not a fit?">
            <div className="mt-1.5 flex gap-2">
              <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} className={fieldControl + " mt-0"} />
              <button
                type="button"
                className={buttonPrimary}
                disabled={!reason.trim() || run.isPending}
                onClick={() => run.mutate(() => changeLeadStatus(lead.id, "DISQUALIFIED", reason.trim()))}
              >
                Disqualify
              </button>
            </div>
          </Field>
        )}

        {canAssign && open && (
          <Field label="Assigned to">
            <select
              value={lead.owner?.id ?? ""}
              disabled={run.isPending}
              onChange={(e) => run.mutate(() => assignLead(lead.id, e.target.value || null))}
              className={fieldControl}
            >
              <option value="">Unassigned</option>
              {(people.data ?? []).map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
          </Field>
        )}

        {open && (
          <div className="rounded-lg bg-canvas p-3.5 text-[13px] text-ink-2 ring-1 ring-line ring-inset">
            <p className="font-medium text-ink">Convert to a deal</p>
            <p className="mt-0.5">Creates the company, the contact and a deal in the pipeline from this lead.</p>
            <button
              type="button"
              className={buttonPrimary + " mt-3"}
              disabled={run.isPending}
              onClick={() =>
                run.mutate(async () => {
                  await convertLead(lead.id);
                  toast(`${lead.name} is now a deal.`);
                })
              }
            >
              Convert to deal
            </button>
          </div>
        )}
        {lead.status === "CONVERTED" && <p className="text-[13px] text-ink-3">This lead has already become a deal.</p>}
        {error && <FormError>{error}</FormError>}
      </DialogBody>
    </Dialog>
  );
}
