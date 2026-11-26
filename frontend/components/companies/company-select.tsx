"use client";

import { Field, fieldControl } from "@/components/ui/primitives";
import { useCompanyOptions } from "@/hooks/use-company-options";
import type { Ref } from "@/lib/page";
import { useState } from "react";

const NONE = "";
const NEW = "__new__";

/**
 * A company dropdown for forms: "No company", every company the user can see, and "New company…", which
 * reveals a name box. Read the result with {@link companyFromForm}.
 */
export function CompanySelect({ current, enabled = true }: { current?: Ref | null; enabled?: boolean }) {
  const companies = useCompanyOptions(enabled);
  const [choice, setChoice] = useState(current?.id ?? NONE);
  const options = companies.data?.items ?? [];
  // The linked company is always offered, even when it falls outside the first page of companies.
  const missing = current && !options.some((c) => c.id === current.id) ? current : null;

  return (
    <>
      <Field label="Company" optional>
        <select
          name="company"
          value={choice}
          onChange={(e) => setChoice(e.target.value)}
          disabled={companies.isPending && !current}
          className={fieldControl}
        >
          <option value={NONE}>{companies.isPending ? "Loading companies…" : "No company"}</option>
          {missing && <option value={missing.id}>{missing.name}</option>}
          {options.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
          <option value={NEW}>+ New company…</option>
        </select>
      </Field>
      {choice === NEW && (
        <Field label="New company name">
          <input name="newCompany" required maxLength={160} autoComplete="off" autoFocus className={fieldControl} />
        </Field>
      )}
    </>
  );
}

/** What to send for the company chosen in a {@link CompanySelect}. `clear` is for edits, where "No company" must unlink. */
export function companyFromForm(f: Record<string, string>, clear = false) {
  if (f.company === NEW) return { companyName: f.newCompany };
  if (f.company) return { companyId: f.company };
  return clear ? { companyName: "" } : {};
}
