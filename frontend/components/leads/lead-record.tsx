"use client";

import { useCurrency } from "@/components/currency/currency-provider";
import { Card, Page, PageHeader, Avatar, StageBadge, buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { DeleteDialog } from "@/components/ui/delete-dialog";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { listActivities, logActivity } from "@/lib/activities";
import { FOLLOW_UP_TYPE_LABELS, listFollowUps } from "@/lib/follow-ups";
import { formatDate, formatDay, localIso, todayLocal } from "@/lib/format";
import { deleteLeads, getLead, type Lead } from "@/lib/leads";
import { moneyValue } from "@/lib/money";
import { AFTER, KEYS } from "@/lib/query-keys";
import { sourceLabel } from "@/lib/stages";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, Building2, Mail, Phone, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { EditLeadDialog } from "./edit-lead-dialog";
import { LeadActionsDialog } from "./lead-actions-dialog";
import { NewFollowUpDialog } from "@/components/follow-ups/new-follow-up-dialog";

export function LeadRecord({ id }: { id: string }) {
  const { format } = useCurrency();
  const refresh = useRefresh();
  const router = useRouter();
  const [acting, setActing] = useState<Lead | null>(null);
  const [editing, setEditing] = useState<Lead | null>(null);
  const [deleting, setDeleting] = useState<Lead | null>(null);
  const [addingTask, setAddingTask] = useState(false);
  const [note, setNote] = useState("");
  const lead = useQuery({ queryKey: [...KEYS.leads, "record", id], queryFn: () => getLead(id) });
  const history = useQuery({ queryKey: [...KEYS.activities, "lead", id], queryFn: () => listActivities({ leadId: id, size: 50 }) });
  const tasks = useQuery({ queryKey: [...KEYS.followUps, "lead", id], queryFn: () => listFollowUps({ leadId: id, size: 50 }) });
  const addNote = useMutation({
    mutationFn: () => logActivity({ type: "NOTE", subject: "Note added", body: note.trim(), leadId: id }),
    onSuccess: () => { setNote(""); void refresh(AFTER.activity); toast("Note added to this lead."); },
    onError: (error) => toast(errorMessage(error, "The note could not be saved.")),
  });
  const c = lead.data;
  if (lead.isError) return <Page><PageHeader title="Lead unavailable" subtitle="The record could not be loaded." /><Card className="p-6"><button onClick={() => lead.refetch()} className={buttonSecondary}>Try again</button></Card></Page>;
  if (!c) return <Page><div className="h-52 animate-pulse rounded-xl bg-surface motion-reduce:animate-none" role="status" aria-label="Loading lead" /></Page>;

  return <Page>
    <div className="mb-4"><Link href="/leads" className="inline-flex items-center gap-1.5 text-[12.5px] text-ink-3 hover:text-brand"><ArrowLeft className="size-3.5" />All leads</Link></div>
    <PageHeader title={c.name} subtitle={c.companyName ?? "Independent lead"}>
      <button onClick={() => setEditing(c)} className={buttonSecondary}>Edit lead</button>
      <button onClick={() => setActing(c)} className={buttonPrimary}>Actions</button>
    </PageHeader>
    <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div className="space-y-5">
        <Card>
          <div className="flex flex-wrap items-center gap-4 border-b border-line p-5"><Avatar name={c.name} size="md" /><div className="min-w-0 flex-1"><h2 className="text-[17px] font-semibold">{c.name}</h2><p className="text-[12.5px] text-ink-3">{c.jobTitle ?? "Lead"}{c.companyName ? ` · ${c.companyName}` : ""}</p></div><StageBadge stage={c.status} /></div>
          <div className="grid gap-x-6 gap-y-4 p-5 text-[13px] sm:grid-cols-2">
            <Fact label="Email" value={c.email ? <a href={`mailto:${c.email}`} className="text-brand hover:underline">{c.email}</a> : "Not provided"} />
            <Fact label="Phone" value={c.phone ? <a href={`tel:${c.phone.replace(/[^+\d]/g, "")}`} className="text-brand hover:underline">{c.phone}</a> : "Not provided"} />
            <Fact label="Source" value={sourceLabel(c.source)} />
            <Fact label="Owner" value={c.owner?.name ?? "Unassigned"} />
            <Fact label="Estimated value" value={c.estimatedValue ? format(moneyValue(c.estimatedValue), c.estimatedValue.currency) : "Not set"} />
            <Fact label="Created" value={formatDate(c.createdAt)} />
          </div>
          {c.disqualifyReason && <p className="border-t border-line px-5 py-4 text-[13px] text-ink-2"><span className="font-medium text-ink">Disqualified:</span> {c.disqualifyReason}</p>}
          {c.convertedDealId && <p className="border-t border-line px-5 py-4 text-[13px]"><Link href={`/deals/${c.convertedDealId}`} className="font-medium text-brand hover:underline">Open converted opportunity →</Link></p>}
        </Card>
        <Card>
          <div className="border-b border-line px-5 py-4"><h2 className="text-[15px] font-semibold">Relationship timeline</h2><p className="mt-0.5 text-[12px] text-ink-3">Calls, meetings and notes linked to this lead.</p></div>
          <form onSubmit={(event) => { event.preventDefault(); if (note.trim()) addNote.mutate(); }} className="flex gap-2 border-b border-line p-4"><input value={note} onChange={(event) => setNote(event.target.value)} placeholder="Add a note…" aria-label="Add a note" maxLength={2000} className={fieldControl + " flex-1"} /><button className={buttonSecondary} disabled={!note.trim() || addNote.isPending}><Plus className="size-4" />Add</button></form>
          {history.isError ? <div className="p-5 text-[13px] text-down">Activity could not be loaded. <button onClick={() => history.refetch()} className="underline">Retry</button></div> : history.isPending ? <p className="p-5 text-[13px] text-ink-3">Loading activity…</p> : history.data?.items.length ? <ol className="divide-y divide-line">{history.data.items.map((item) => <li key={item.id} className="px-5 py-4"><div className="flex items-baseline justify-between gap-3"><p className="text-[13px] font-medium">{item.subject}</p><time className="tnum shrink-0 text-[11px] text-ink-3" dateTime={item.occurredAt}>{formatDay(localIso(item.occurredAt))}</time></div><p className="mt-1 text-[12.5px] text-ink-2">{item.body ?? item.type}</p><p className="mt-1 text-[11px] text-ink-3">{item.actorName ?? "System"}</p></li>)}</ol> : <p className="p-5 text-[13px] text-ink-3">No activity yet. Add a note to start the timeline.</p>}
        </Card>
      </div>
      <aside className="space-y-5">
        <Card className="p-5"><h2 className="text-[12px] font-semibold uppercase tracking-wide text-ink-3">Quick contact</h2><div className="mt-4 space-y-2">{c.email && <a href={`mailto:${c.email}`} className={buttonSecondary + " w-full justify-center"}><Mail className="size-4" />Email</a>}{c.phone && <a href={`tel:${c.phone.replace(/[^+\d]/g, "")}`} className={buttonSecondary + " w-full justify-center"}><Phone className="size-4" />Call</a>}{c.companyName && <p className="flex items-center gap-2 pt-2 text-[12.5px] text-ink-2"><Building2 className="size-4" />{c.companyName}</p>}</div></Card>
        <Card><div className="border-b border-line px-5 py-4"><h2 className="text-[15px] font-semibold">Follow-ups</h2></div>{tasks.isError ? <p className="p-5 text-[13px] text-down">Follow-ups could not be loaded.</p> : tasks.isPending ? <p className="p-5 text-[13px] text-ink-3">Loading…</p> : tasks.data?.items.length ? <ul className="divide-y divide-line">{tasks.data.items.map((item) => <li key={item.id} className="px-5 py-3 text-[12.5px]"><p className="font-medium">{item.title}</p><p className="mt-1 text-ink-3">{FOLLOW_UP_TYPE_LABELS[item.type]} · {formatDay(localIso(item.dueAt))} · {item.status.toLowerCase()}</p></li>)}</ul> : <p className="p-5 text-[13px] text-ink-3">Nothing scheduled.</p>}<div className="border-t border-line p-4"><button onClick={() => setAddingTask(true)} className={buttonSecondary + " w-full justify-center"}><Plus className="size-4" />New task</button></div></Card>
      </aside>
    </div>
    <LeadActionsDialog lead={acting} onClose={() => setActing(null)} onEdit={setEditing} onDelete={setDeleting} />
    <EditLeadDialog lead={editing} onClose={() => setEditing(null)} />
    {addingTask && <NewFollowUpDialog open onClose={() => setAddingTask(false)} defaultDay={todayLocal()} defaultRelated={{ type: "lead", id }} onAdded={() => { void refresh(AFTER.followUp); toast("Follow-up scheduled for this lead."); }} />}
    <DeleteDialog target={deleting} title={(item) => `Delete ${item.name}?`} body="The lead moves to the recycle bin and can be restored." successMessage={(item) => `${item.name} moved to the recycle bin.`} action={(item) => deleteLeads([item.id])} onClose={() => setDeleting(null)} onDeleted={() => { void refresh(AFTER.lead); router.push("/leads"); }} />
  </Page>;
}

function Fact({ label, value }: { label: string; value: React.ReactNode }) {
  return <div><p className="text-[11px] font-medium uppercase tracking-wide text-ink-3">{label}</p><div className="mt-1 font-medium text-ink">{value}</div></div>;
}
