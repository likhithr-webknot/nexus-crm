"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { MODULES } from "@/components/modules/module-config";
import type { ModuleRecord, ModuleKind } from "@/lib/modules";

const schema = z.object({
  name: z.string().trim().min(2, "Enter at least two characters."),
  status: z.string().min(1),
  account: z.string(),
  description: z.string(),
  amount: z.number().min(0, "Value cannot be negative."),
});
type Values = z.infer<typeof schema>;

export function RecordForm({ kind, record, open, onClose, onSave }: { kind: ModuleKind; record?: ModuleRecord; open: boolean; onClose: () => void; onSave: (values: Values) => Promise<void> }) {
  const config = MODULES[kind];
  const [saveError, setSaveError] = useState("");
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: record ? { name: record.name, status: record.status, account: record.account, description: record.description, amount: record.amount } : { name: "", status: config.statuses[0], account: "", description: "", amount: 0 } });
  const submit = handleSubmit(async (values) => { try { await onSave(values); onClose(); } catch { setSaveError("The record could not be saved. Try again."); } });

  return <Dialog.Root open={open} onOpenChange={(value) => { if (!value) onClose(); }}><Dialog.Portal><Dialog.Overlay className="fixed inset-0 z-50 bg-scrim" /><Dialog.Content className="fixed inset-y-0 right-0 z-50 flex w-full max-w-[480px] flex-col border-l border-line bg-surface text-ink shadow-2xl data-[state=open]:animate-[drawer-in_260ms_var(--ease-enter)]"><div className="border-b border-line px-6 py-5"><Dialog.Title className="text-[18px] font-semibold">{record ? "Edit" : "New"} {config.singular}</Dialog.Title><Dialog.Description className="mt-1 text-[12.5px] text-ink-2">{config.note}</Dialog.Description></div><form onSubmit={submit} className="flex min-h-0 flex-1 flex-col"><div className="flex-1 space-y-5 overflow-y-auto px-6 py-5"><label className="block text-[12px] font-medium text-ink-2">Name<input {...register("name")} className={fieldControl} autoFocus />{errors.name && <span role="alert" className="mt-1 block text-[11px] text-down">{errors.name.message}</span>}</label><label className="block text-[12px] font-medium text-ink-2">Status<select {...register("status")} className={fieldControl}>{config.statuses.map((status) => <option key={status}>{status}</option>)}</select></label>{kind !== "products" && kind !== "automations" && <label className="block text-[12px] font-medium text-ink-2">Related account<input {...register("account")} className={fieldControl} placeholder="Optional" /></label>}{config.metric && <label className="block text-[12px] font-medium text-ink-2">{config.metric}<input {...register("amount", { valueAsNumber: true })} type="number" min="0" step={kind === "automations" ? "1" : "0.01"} className={fieldControl} />{errors.amount && <span role="alert" className="mt-1 block text-[11px] text-down">{errors.amount.message}</span>}</label>}<label className="block text-[12px] font-medium text-ink-2">{kind === "communications" ? "Message / call notes" : "Description"}<textarea {...register("description")} rows={6} className={`${fieldControl} h-auto py-2`} /></label>{saveError && <p role="alert" className="text-[12px] text-down">{saveError}</p>}</div><div className="flex justify-end gap-2 border-t border-line bg-canvas/50 px-6 py-4"><button type="button" onClick={onClose} className={buttonSecondary}>Cancel</button><button type="submit" disabled={isSubmitting} className={buttonPrimary}>{isSubmitting ? "Saving…" : record ? "Save changes" : `Create ${config.singular}`}</button></div></form></Dialog.Content></Dialog.Portal></Dialog.Root>;
}
