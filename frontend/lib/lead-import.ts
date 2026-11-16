import { parseCsv } from "@/lib/csv";
import type { ImportRow } from "@/lib/leads";
import { SOURCES, SOURCE_LABELS } from "@/lib/stages";

export const MAX_IMPORT_FILE_BYTES = 1_000_000;

const EMAIL = /^\S+@\S+\.\S+$/;

export type ParsedImport = { rows: ImportRow[]; missing: number; badEmail: number; repeated: number };

/** Turns CSV text into import rows. Rows without a name or a valid email are counted, not kept. The server checks the rest. */
export function readLeadRows(text: string, defaultCurrency: string): ParsedImport | string {
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
  const result: ParsedImport = { rows: [], missing: 0, badEmail: 0, repeated: 0 };
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
