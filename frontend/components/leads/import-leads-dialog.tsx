"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { Dialog, DialogActions, DialogBody, FormError } from "@/components/ui/dialog";
import { Field } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { ApiError, errorMessage } from "@/lib/api";
import { downloadCsv } from "@/lib/csv";
import { MAX_IMPORT_FILE_BYTES, readLeadRows, type ParsedImport } from "@/lib/lead-import";
import { importLeads } from "@/lib/leads";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

export function ImportLeadsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { reportingCurrency } = useCurrency();
  const refresh = useRefresh();
  const [parsed, setParsed] = useState<ParsedImport | string | null>(null);
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
      void refresh(AFTER.lead);
      close();
    },
    onError: (e) => setError(e instanceof ApiError && e.status === 413 ? "That file is too large." : errorMessage(e)),
  });

  const pick = async (file: File | undefined) => {
    setError(null);
    if (!file) return setParsed(null);
    if (file.size > MAX_IMPORT_FILE_BYTES)
      return setParsed("That file is larger than 1 MB. Split it into smaller files.");
    setParsed(readLeadRows(await file.text(), reportingCurrency));
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
        <Field
          label="CSV file"
          hint="Needs Name and Email columns. Company, Phone, Source, Estimated value and Currency are optional. Up to 1 MB."
        >
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
            downloadCsv(
              "leads-template.csv",
              ["Name", "Company", "Email", "Phone", "Source", "Estimated value", "Currency"],
              [
                [
                  "Jane Doe",
                  "Acme Inc.",
                  "jane@acme.example",
                  "+1 (415) 555-0100",
                  "Referral",
                  "15000",
                  reportingCurrency,
                ],
              ],
            )
          }
          className="justify-self-start text-[13px] font-medium text-brand hover:underline"
        >
          Download a template
        </button>

        {typeof parsed === "string" && <FormError>{parsed}</FormError>}
        {error && <FormError>{error}</FormError>}
        {result && (
          <div
            role="status"
            className="rounded-lg bg-canvas px-3.5 py-3 text-[13px] text-ink-2 ring-1 ring-line ring-inset"
          >
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
