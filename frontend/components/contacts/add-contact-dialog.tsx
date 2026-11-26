"use client";

import { CompanySelect, companyFromForm } from "@/components/companies/company-select";
import { useSession } from "@/components/auth/session";
import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useOwnerOptions } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { createContact, type Contact } from "@/lib/contacts";
import { splitName } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

export function AddContactDialog({
  open,
  onClose,
  onAdded,
}: {
  open: boolean;
  onClose: () => void;
  onAdded: (contact: Contact) => void;
}) {
  const session = useSession();
  const owners = useOwnerOptions(open);
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: createContact,
    onSuccess: (contact) => {
      toast(`${contact.name} added.`);
      void refresh(AFTER.contact);
      onAdded(contact);
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      open={open}
      onClose={onClose}
      title="Add contact"
      submitLabel="Add contact"
      pending={add.isPending}
      error={error}
      onSubmit={(f) => {
        setError(null);
        add.mutate({
          ...splitName(f.name),
          jobTitle: f.title || undefined,
          ...companyFromForm(f),
          ownerId: f.owner || undefined,
          email: f.email || undefined,
          phone: f.phone || undefined,
        });
      }}
    >
      <Field label="Full name">
        <input name="name" required maxLength={240} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Job title" optional>
        <input name="title" maxLength={120} autoComplete="off" className={fieldControl} />
      </Field>
      <CompanySelect enabled={open} />
      <Field label="Owner">
        <select name="owner" defaultValue={session.id} className={fieldControl}>
          {owners.map((u) => (
            <option key={u.id} value={u.id}>
              {u.displayName}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Email">
        <input name="email" type="email" required maxLength={254} autoComplete="off" className={fieldControl} />
      </Field>
      <Field label="Phone" optional>
        <input name="phone" type="tel" maxLength={40} autoComplete="off" className={fieldControl} />
      </Field>
    </FormDialog>
  );
}
