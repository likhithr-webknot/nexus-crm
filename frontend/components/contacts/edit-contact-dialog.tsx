"use client";

import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { updateContact, type Contact } from "@/lib/contacts";
import { splitName } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

export function EditContactDialog({
  contact,
  open,
  onClose,
}: {
  contact: Contact;
  open: boolean;
  onClose: () => void;
}) {
  const refresh = useRefresh();
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setError(null);
    onClose();
  };

  const save = useMutation({
    mutationFn: (changes: Parameters<typeof updateContact>[1]) => updateContact(contact.id, changes),
    onSuccess: (updated) => {
      toast(`${updated.name} updated.`);
      void refresh(AFTER.contact);
      close();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <FormDialog
      key={`${contact.id}-${contact.version}`}
      open={open}
      onClose={close}
      title="Edit contact"
      submitLabel="Save changes"
      pending={save.isPending}
      error={error}
      onSubmit={(f) => {
        setError(null);
        save.mutate({
          ...splitName(f.name),
          jobTitle: f.title,
          companyName: f.company || undefined,
          email: f.email,
          phone: f.phone,
          version: contact.version,
        });
      }}
    >
      <Field label="Full name">
        <input
          name="name"
          required
          maxLength={240}
          defaultValue={contact.name}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
      <Field label="Job title" optional>
        <input
          name="title"
          maxLength={120}
          defaultValue={contact.jobTitle ?? ""}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
      <Field label="Company" optional>
        <input
          name="company"
          maxLength={160}
          defaultValue={contact.company?.name ?? ""}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
      <Field label="Email">
        <input
          name="email"
          type="email"
          required
          maxLength={254}
          defaultValue={contact.email ?? ""}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
      <Field label="Phone" optional>
        <input
          name="phone"
          type="tel"
          maxLength={40}
          defaultValue={contact.phone ?? ""}
          autoComplete="off"
          className={fieldControl}
        />
      </Field>
    </FormDialog>
  );
}
