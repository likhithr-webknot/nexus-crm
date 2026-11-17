"use client";

import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { useMutation } from "@tanstack/react-query";
import { Activity, Building2, Contact, Handshake, ListTodo, NotebookPen, Plus, UserPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AddLeadDialog } from "@/components/leads/add-lead-dialog";
import { AddContactDialog } from "@/components/contacts/add-contact-dialog";
import { AddDealDialog } from "@/components/pipeline/add-deal-dialog";
import { FormDialog } from "@/components/ui/dialog";
import { Field, buttonPrimary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api";
import { createCompany } from "@/lib/contacts";
import { NewFollowUpDialog } from "@/components/follow-ups/new-follow-up-dialog";
import { LogActivityDialog } from "@/components/activities/log-activity-dialog";
import { todayLocal } from "@/lib/format";

type Kind = "lead" | "contact" | "company" | "deal" | "task" | "activity" | "note" | null;

export function QuickCreate() {
  const [kind, setKind] = useState<Kind>(null);
  const router = useRouter();
  useEffect(() => {
    const open = (event: Event) => {
      const next = (event as CustomEvent<Kind>).detail;
      if (
        next === "lead" ||
        next === "contact" ||
        next === "company" ||
        next === "deal" ||
        next === "task" ||
        next === "activity" ||
        next === "note"
      )
        setKind(next);
    };
    window.addEventListener("nexus:create", open);
    return () => window.removeEventListener("nexus:create", open);
  }, []);
  const company = useMutation({
    mutationFn: createCompany,
    onSuccess: (record) => {
      toast(`${record.name} added.`);
      setKind(null);
      router.push("/companies");
    },
  });

  const actions = [
    { kind: "lead" as const, label: "Lead", icon: UserPlus },
    { kind: "contact" as const, label: "Contact", icon: Contact },
    { kind: "company" as const, label: "Company", icon: Building2 },
    { kind: "deal" as const, label: "Deal", icon: Handshake },
    { kind: "task" as const, label: "Task", icon: ListTodo },
    { kind: "activity" as const, label: "Activity", icon: Activity },
    { kind: "note" as const, label: "Note", icon: NotebookPen },
  ];

  return (
    <>
      <DropdownMenu.Root>
        <DropdownMenu.Trigger className={`${buttonPrimary} h-8 px-3`} aria-label="Quick create">
          <Plus className="size-4" aria-hidden />
          <span className="hidden sm:inline">Create</span>
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <DropdownMenu.Content
            sideOffset={8}
            align="end"
            className="z-50 w-48 rounded-xl border border-line bg-surface p-1.5 text-ink shadow-xl data-[state=open]:animate-[menu-in_var(--duration-menu)_var(--ease-enter)]"
          >
            <DropdownMenu.Label className="px-2 py-1.5 text-[11px] font-semibold tracking-wide text-ink-3 uppercase">
              Create new
            </DropdownMenu.Label>
            {actions.map(({ kind: value, label, icon: Icon }) => (
              <DropdownMenu.Item
                key={value}
                onSelect={() => setKind(value)}
                className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-2 text-[13px] outline-none focus:bg-canvas"
              >
                <Icon className="size-4 text-ink-2" aria-hidden />
                {label}
              </DropdownMenu.Item>
            ))}
          </DropdownMenu.Content>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
      {kind === "lead" && <AddLeadDialog open onClose={() => setKind(null)} />}
      {kind === "contact" && (
        <AddContactDialog open onClose={() => setKind(null)} onAdded={() => router.push("/contacts")} />
      )}
      {kind === "deal" && <AddDealDialog open onClose={() => setKind(null)} onAdded={() => router.push("/pipeline")} />}
      {kind === "task" && (
        <NewFollowUpDialog
          open
          onClose={() => setKind(null)}
          defaultDay={todayLocal()}
          onAdded={() => router.push("/tasks")}
        />
      )}
      {(kind === "activity" || kind === "note") && (
        <LogActivityDialog
          open
          onClose={() => setKind(null)}
          onLogged={() => router.push("/activities")}
          defaultType={kind === "note" ? "NOTE" : "CALL"}
        />
      )}
      {kind === "company" && (
        <FormDialog
          open
          onClose={() => setKind(null)}
          title="Add company"
          submitLabel="Add company"
          pending={company.isPending}
          error={company.error ? errorMessage(company.error) : null}
          onSubmit={(values) => company.mutate({ name: values.name, domain: values.domain, industry: values.industry })}
        >
          <Field label="Company name">
            <input name="name" required autoComplete="organization" className={fieldControl} />
          </Field>
          <Field label="Website domain" optional>
            <input name="domain" placeholder="example.com" className={fieldControl} />
          </Field>
          <Field label="Industry" optional>
            <input name="industry" className={fieldControl} />
          </Field>
        </FormDialog>
      )}
    </>
  );
}
