"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CalendarDays, Clock3, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useCurrency } from "@/components/currency/currency-provider";
import { EditDealDialog } from "@/components/pipeline/edit-deal-dialog";
import { DeleteDialog } from "@/components/ui/delete-dialog";
import { Avatar, Card, Page, StageBadge, buttonSecondary } from "@/components/ui/primitives";
import { listActivities } from "@/lib/activities";
import { deleteDeal, getDeal, listStages, type Deal } from "@/lib/deals";
import { listFollowUps } from "@/lib/follow-ups";
import { formatDay, localIso } from "@/lib/format";
import { moneyValue } from "@/lib/money";
import { KEYS } from "@/lib/query-keys";

export function DealDetail({ id }: { id: string }) {
  const [editing, setEditing] = useState<Deal | null>(null);
  const [deleting, setDeleting] = useState<Deal | null>(null);
  const router = useRouter();
  const queryClient = useQueryClient();
  const { format } = useCurrency();
  const deal = useQuery({ queryKey: [...KEYS.deals, "detail", id], queryFn: () => getDeal(id) });
  const stages = useQuery({ queryKey: KEYS.stages, queryFn: listStages });
  const activities = useQuery({ queryKey: [...KEYS.activities, "deal", id], queryFn: () => listActivities({ dealId: id, size: 20 }) });
  const followUps = useQuery({ queryKey: [...KEYS.followUps, "deal", id], queryFn: () => listFollowUps({ dealId: id, status: "OPEN", size: 10 }) });
  const data = deal.data;
  const refresh = () => { void queryClient.invalidateQueries({ queryKey: KEYS.deals }); };

  if (deal.isError) return <Page><Link href="/deals" className={buttonSecondary}><ArrowLeft className="size-4" />Back to deals</Link><Card className="p-8 text-[13px] text-down">This deal could not be loaded. <button className="underline" onClick={() => deal.refetch()}>Retry</button></Card></Page>;
  if (!data) return <Page><div className="h-10 w-28 animate-pulse rounded-lg bg-surface motion-reduce:animate-none" /><Card className="h-72 animate-pulse motion-reduce:animate-none" /></Page>;
  const currentStage = stages.data?.find((stage) => stage.code === data.stageCode);
  const stageIndex = stages.data?.findIndex((stage) => stage.code === data.stageCode) ?? -1;

  return <Page>
    <div className="flex flex-wrap items-center justify-between gap-3"><Link href="/deals" className="inline-flex items-center gap-1.5 text-[12.5px] font-medium text-ink-2 hover:text-ink"><ArrowLeft className="size-4" />All deals</Link><div className="flex gap-2"><button onClick={() => setEditing(data)} className={buttonSecondary}><Pencil className="size-4" />Edit</button><button onClick={() => setDeleting(data)} className={buttonSecondary} aria-label="Delete deal"><Trash2 className="size-4" /></button></div></div>
    <div className="grid items-start gap-5 xl:grid-cols-[180px_minmax(0,1fr)_275px]">
      <aside className="hidden xl:block"><Card className="sticky top-6 p-3"><p className="px-2 py-2 text-[11px] font-semibold tracking-wide text-ink-3 uppercase">In this deal</p><nav aria-label="Deal sections" className="space-y-0.5 text-[12.5px]"><a href="#overview" className="block rounded-md bg-brand-soft px-2 py-2 font-medium text-brand">Overview</a><a href="#activity" className="block rounded-md px-2 py-2 text-ink-2 hover:bg-canvas">Activity</a><a href="#next-steps" className="block rounded-md px-2 py-2 text-ink-2 hover:bg-canvas">Next steps</a></nav><Link href="/pipeline" className="mt-4 block border-t border-line px-2 pt-3 text-[12px] font-medium text-brand">Open pipeline →</Link></Card></aside>
      <div className="min-w-0 space-y-5">
        <Card className="overflow-hidden"><div id="overview" className="p-6"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-[11px] font-semibold tracking-wide text-ink-3 uppercase">Opportunity · {data.company.name}</p><h1 className="mt-1 text-[25px] font-semibold tracking-tight">{data.name}</h1><p className="tnum mt-3 text-[30px] font-semibold tracking-tight">{format(moneyValue(data.amount), data.amount.currency)}</p></div><StageBadge stage={data.stageCode} /></div></div><div className="border-t border-line px-6 py-5"><div className="mb-3 flex items-center justify-between"><h2 className="text-[13px] font-semibold">Deal progress</h2><span className="text-[12px] text-ink-3">{currentStage?.name ?? data.stageCode}</span></div><ol className="grid grid-flow-col auto-cols-fr gap-1" aria-label="Pipeline stages">{stages.data?.filter((stage) => stage.kind !== "LOST").map((stage, index) => <li key={stage.code} title={stage.name} className="min-w-0"><span className={`block h-1.5 rounded-full ${index <= stageIndex ? "bg-brand" : "bg-canvas"}`} /><span className="mt-1 block truncate text-[10px] text-ink-3">{stage.name}</span></li>)}</ol></div><dl className="grid gap-4 border-t border-line p-6 text-[12.5px] sm:grid-cols-2"><div><dt className="text-ink-3">Company</dt><dd className="mt-1 font-medium"><Link href={`/companies/${data.company.id}`} className="text-brand hover:underline">{data.company.name}</Link></dd></div><div><dt className="text-ink-3">Primary contact</dt><dd className="mt-1 font-medium">{data.primaryContact?.name ?? "Not linked"}</dd></div><div><dt className="text-ink-3">Expected close</dt><dd className="tnum mt-1 font-medium">{data.expectedCloseDate ? formatDay(data.expectedCloseDate) : "Not set"}</dd></div><div><dt className="text-ink-3">Created</dt><dd className="tnum mt-1 font-medium">{formatDay(data.createdAt)}</dd></div></dl></Card>
        <Card id="activity"><div className="border-b border-line px-5 py-4"><h2 className="text-[15px] font-semibold">Activity timeline</h2><p className="mt-0.5 text-[12px] text-ink-3">Calls, notes and updates tied to this opportunity</p></div>{activities.isError ? <p className="p-5 text-[13px] text-down">Activity could not be loaded. <button onClick={() => activities.refetch()} className="underline">Retry</button></p> : activities.isPending ? <p className="p-5 text-[13px] text-ink-3">Loading activity…</p> : activities.data?.items.length ? <ol className="divide-y divide-line px-5">{activities.data.items.map((entry) => <li key={entry.id} className="flex gap-3 py-4"><span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-brand-soft text-brand"><Clock3 className="size-3.5" /></span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="text-[13px] font-medium">{entry.subject}</h3><time className="tnum text-[11px] text-ink-3">{formatDay(localIso(entry.occurredAt))}</time></div><p className="mt-0.5 text-[11px] text-ink-3">{entry.actorName ?? "System"} · {entry.type.toLowerCase()}</p>{entry.body && <p className="mt-2 text-[12.5px] text-ink-2">{entry.body}</p>}</div></li>)}</ol> : <p className="p-5 text-[13px] text-ink-3">No activity has been logged for this deal.</p>}</Card>
      </div>
      <aside className="space-y-5"><Card className="p-5"><h2 className="text-[13px] font-semibold">Deal owner</h2><div className="mt-3 flex items-center gap-2.5"><Avatar name={data.owner.name} size="md" /><span className="text-[13px] font-medium">{data.owner.name}</span></div><div className="mt-5 space-y-3 border-t border-line pt-4 text-[12.5px]"><p className="flex items-center gap-2 text-ink-2"><CalendarDays className="size-4" />Close {data.expectedCloseDate ? formatDay(data.expectedCloseDate) : "date not set"}</p><p className="flex items-center gap-2 text-ink-2"><Clock3 className="size-4" />{currentStage?.winProbability ?? 0}% stage probability</p></div></Card><Card id="next-steps"><div className="border-b border-line px-5 py-4"><h2 className="text-[13px] font-semibold">Next steps</h2></div>{followUps.isError ? <p className="p-5 text-[12.5px] text-down">Follow-ups could not be loaded.</p> : followUps.data?.items.length ? <ul className="divide-y divide-line">{followUps.data.items.map((item) => <li key={item.id} className="px-5 py-3"><p className="text-[12.5px] font-medium">{item.title}</p><p className="tnum mt-1 text-[11px] text-ink-3">{formatDay(localIso(item.dueAt))}</p></li>)}</ul> : <p className="p-5 text-[12.5px] text-ink-3">No open follow-ups yet.</p>}</Card></aside>
    </div>
    <EditDealDialog deal={editing} onClose={() => setEditing(null)} onSaved={refresh} onDelete={setDeleting} />
    <DeleteDialog target={deleting} title={(item) => `Delete ${item.name}?`} body="This removes the opportunity from the pipeline." successMessage={(item) => `${item.name} deleted.`} action={(item) => deleteDeal(item.id)} onClose={() => setDeleting(null)} onDeleted={() => { refresh(); router.push("/deals"); }} />
  </Page>;
}
