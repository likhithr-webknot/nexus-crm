"use client";

import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { getCompany, updateCompany } from "@/lib/contacts";
import type { Ref } from "@/lib/page";
import { AFTER, KEYS } from "@/lib/query-keys";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";

/** Changes a company's name, website domain and industry. */
export function EditCompanyDialog({ company, onClose }: { company: Ref | null; onClose: () => void }) {
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);
  const details = useQuery({ queryKey: [...KEYS.companies, company?.id], queryFn: () => getCompany(company!.id), enabled: company !== null });
  const close = () => {
    setError(null);
    onClose();
  };

  const save = useMutation({
    mutationFn: (changes: Parameters<typeof updateCompany>[1]) => updateCompany(company!.id, changes),
    onSuccess: (updated) => {
      toast(`${updated.name} updated.`);
      void refresh([...AFTER.contact, KEYS.companies]);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      key={details.data?.id ?? "loading"}
      open={company !== null}
      onClose={close}
      title="Edit company"
      size="sm"
      submitLabel="Save changes"
      pending={save.isPending}
      disabled={!details.data}
      error={error}
      onSubmit={(f) => {
        setError(null);
        save.mutate({ name: f.name, domain: f.domain, industry: f.industry });
      }}
    >
      <Field label="Company name" className="sm:col-span-2">
        <input name="name" required maxLength={160} defaultValue={details.data?.name ?? company?.name ?? ""} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Website domain" optional>
        <input name="domain" maxLength={160} defaultValue={details.data?.domain ?? ""} placeholder="acme.com" autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Industry" optional>
        <input name="industry" maxLength={120} defaultValue={details.data?.industry ?? ""} autoComplete="off" className={fieldControl} />
      </Field>
    </FormDialog>
  );
}
