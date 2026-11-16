"use client";

import { useState } from "react";
import { OWNERS, SOURCES, TODAY, type Lead, type Source } from "@/lib/mock-data";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { downloadCsv, parseCsv } from "@/lib/csv";
import { readForm } from "@/lib/format";
import { CURRENCIES, currencyMinorUnits, decimalPlaces, isCurrencyCode } from "@/lib/currency";
import { useCurrency } from "@/components/currency/currency-provider";

const EMAIL = /^\S+@\S+\.\S+$/;
const nextId = (leads: Lead[], offset = 0) =>
  `L-${Math.max(1000, ...leads.map((l) => Number(l.id.slice(2)) || 0)) + 1 + offset}`;

export function AddLeadDialog({
  open,
  onClose,
  leads,
  onAdd,
}: {
  open: boolean;
  onClose: () => void;
  leads: Lead[];
  onAdd: (lead: Lead) => void;
}) {
  const { reportingCurrency } = useCurrency();
  // Set when the email belongs to an existing lead. A second submit adds it anyway.
  const [duplicate, setDuplicate] = useState<Lead | null>(null);
  const close = () => {
    setDuplicate(null);
    onClose();
  };

  const submit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = readForm(e.currentTarget);
    const rawValue = f.value ?? "";
    const currency = f.currency ?? reportingCurrency;
    if (!isCurrencyCode(currency)) {
      const input = e.currentTarget.elements.namedItem("currency");
      if (input instanceof HTMLSelectElement) {
        input.setCustomValidity("Choose a supported currency.");
        input.reportValidity();
      }
      return;
    }
    const enteredValue = rawValue ? Number(rawValue) : 0;
    if (!Number.isFinite(enteredValue) || enteredValue < 0) {
      const input = e.currentTarget.elements.namedItem("value");
      if (input instanceof HTMLInputElement) {
        input.setCustomValidity("Enter a non-negative amount.");
        input.reportValidity();
      }
      return;
    }
    if (rawValue && decimalPlaces(rawValue) > currencyMinorUnits(currency)) {
      const input = e.currentTarget.elements.namedItem("value");
      if (input instanceof HTMLInputElement) {
        input.setCustomValidity(`${currency} allows up to ${currencyMinorUnits(currency)} decimal places.`);
        input.reportValidity();
      }
      return;
    }
    const existing = leads.find((l) => l.email.toLowerCase() === f.email.toLowerCase());
    if (existing && !duplicate) return setDuplicate(existing);
    onAdd({
      id: nextId(leads),
      name: f.name,
      company: f.company,
      email: f.email,
      phone: f.phone,
      source: f.source as Source,
      owner: f.owner || null,
      stage: "New",
      value: enteredValue,
      currency,
      createdAt: TODAY,
    });
    close();
  };

  return (
    <Dialog open={open} onClose={close} title="Add lead" description="New leads start in the New stage.">
      <form onSubmit={submit}>
        <DialogBody className="sm:grid-cols-2">
          <Field label="Full name">
            <input name="name" required maxLength={120} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Company">
            <input name="company" required maxLength={120} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Email">
            <input
              name="email"
              type="email"
              required
              maxLength={254}
              autoComplete="off"
              onChange={() => setDuplicate(null)}
              className={fieldControl}
            />
          </Field>
          <Field label="Phone" optional>
            <input name="phone" type="tel" maxLength={40} autoComplete="off" className={fieldControl} />
          </Field>
          <Field label="Source">
            <select name="source" defaultValue="Manual Entry" className={fieldControl}>
              {SOURCES.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </Field>
          <Field label="Assigned to">
            <select name="owner" className={fieldControl}>
              <option value="">Unassigned</option>
              {OWNERS.map((o) => (
                <option key={o}>{o}</option>
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
              {CURRENCIES.map(({ code, name }) => (
                <option key={code} value={code}>
                  {code} — {name}
                </option>
              ))}
            </select>
          </Field>
          {duplicate && (
            <div className="sm:col-span-2">
              <FormError>
                {duplicate.name} at {duplicate.company} already uses this email. Add this lead anyway, or cancel.
              </FormError>
            </div>
          )}
        </DialogBody>
        <DialogActions onCancel={close} submitLabel={duplicate ? "Add anyway" : "Add lead"} />
      </form>
    </Dialog>
  );
}

const MAX_FILE_BYTES = 1_000_000;

type Parsed = { ready: Lead[]; missing: number; badEmail: number; duplicates: number; invalidAmount: number; invalidCurrency: number };

/** Turns CSV text into leads. Rows that cannot become a lead are counted, not imported. */
function readLeads(text: string, existing: Lead[], defaultCurrency: string): Parsed | string {
  const [header, ...rows] = parseCsv(text);
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

  const seen = new Set(existing.map((l) => l.email.toLowerCase()));
  const result: Parsed = { ready: [], missing: 0, badEmail: 0, duplicates: 0, invalidAmount: 0, invalidCurrency: 0 };
  for (const row of rows) {
    // Our own export puts a quote before cells a spreadsheet could run as a formula; take it off again.
    const cell = (i: number) => (i < 0 ? "" : (row[i] ?? "").trim().replace(/^'(?=[=+\-@])/, ""));
    const name = cell(at.name);
    const email = cell(at.email).toLowerCase();
    const rawValue = cell(at.value);
    const value = rawValue ? Number(rawValue) : 0;
    const currency = cell(at.currency) || defaultCurrency;
    if (!name || !email) result.missing++;
    else if (!EMAIL.test(email)) result.badEmail++;
    else if (seen.has(email)) result.duplicates++;
    else if (!Number.isFinite(value) || value < 0)
      result.invalidAmount++;
    else if (!isCurrencyCode(currency)) result.invalidCurrency++;
    else if (decimalPlaces(rawValue) > currencyMinorUnits(currency)) result.invalidAmount++;
    else {
      seen.add(email);
      const source = SOURCES.find((s) => s.toLowerCase() === cell(at.source).toLowerCase()) ?? "Manual Entry";
      result.ready.push({
        id: nextId(existing, result.ready.length),
        name,
        email,
        company: cell(at.company),
        phone: cell(at.phone),
        source,
        owner: null,
        stage: "New",
        value,
        currency,
        createdAt: TODAY,
      });
    }
  }
  return result;
}

export function ImportLeadsDialog({
  open,
  onClose,
  leads,
  onImport,
}: {
  open: boolean;
  onClose: () => void;
  leads: Lead[];
  onImport: (leads: Lead[]) => void;
}) {
  const { reportingCurrency } = useCurrency();
  const [parsed, setParsed] = useState<Parsed | string | null>(null);
  const close = () => {
    setParsed(null);
    onClose();
  };

  const pick = async (file: File | undefined) => {
    if (!file) return setParsed(null);
    if (file.size > MAX_FILE_BYTES) return setParsed("That file is larger than 1 MB. Split it into smaller files.");
    setParsed(readLeads(await file.text(), leads, reportingCurrency));
  };

  const result = typeof parsed === "object" ? parsed : null;
  const skipped = result
    ? [
        [result.duplicates, "already exist or repeat an email"],
        [result.missing, "have no name or email"],
        [result.badEmail, "have an email that isn't valid"],
        [result.invalidAmount, "have an invalid amount"],
        [result.invalidCurrency, "have an unsupported currency code"],
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
        {result && (
          <div role="status" className="rounded-lg bg-canvas px-3.5 py-3 text-[13px] text-ink-2 ring-1 ring-line ring-inset">
            <p className="font-medium text-ink">
              {result.ready.length === 1 ? "1 lead is" : `${result.ready.length} leads are`} ready to import.
            </p>
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
        disabled={!result?.ready.length}
        submitLabel={result?.ready.length ? `Import ${result.ready.length}` : "Import"}
        onSubmit={() => {
          if (result) onImport(result.ready);
          close();
        }}
      />
    </Dialog>
  );
}
