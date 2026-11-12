import Link from "next/link";
import { UserPlus } from "lucide-react";
import type { Lead } from "@/lib/mock-data";
import { Avatar, StageBadge } from "@/components/ui/primitives";
import { formatDate, formatUsd } from "@/lib/format";

export function RecentLeadsTable({ leads }: { leads: Lead[] }) {
  if (leads.length === 0) {
    return (
      <div className="flex flex-col items-center px-6 py-12 text-center">
        <span className="grid size-10 place-items-center rounded-full bg-brand-soft text-brand">
          <UserPlus className="size-5" />
        </span>
        <p className="mt-3 text-[14px] font-medium">No leads yet</p>
        <p className="mt-1 text-[13px] text-ink-3">New leads from your forms and imports will show up here.</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-[13px]">
        <thead>
          <tr className="border-y border-line bg-canvas/60 text-[11.5px] font-medium tracking-wide text-ink-3 uppercase">
            <th className="py-2.5 pr-3 pl-5 font-medium">Lead</th>
            <th className="px-3 py-2.5 font-medium">Owner</th>
            <th className="px-3 py-2.5 font-medium">Stage</th>
            <th className="px-3 py-2.5 text-right font-medium">Value</th>
            <th className="py-2.5 pr-5 pl-3 text-right font-medium">Created</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {leads.map((lead) => (
            <tr key={lead.id} className="group hover:bg-canvas/60">
              <td className="py-3 pr-3 pl-5">
                <div className="flex items-center gap-3">
                  <Avatar name={lead.name} />
                  <div className="min-w-0">
                    <Link href={`/leads?id=${lead.id}`} className="font-medium text-ink hover:text-brand">
                      {lead.name}
                    </Link>
                    <p className="truncate text-[12px] text-ink-3">
                      {lead.company} · {lead.email}
                    </p>
                  </div>
                </div>
              </td>
              <td className="px-3 py-3">
                <div className="flex items-center gap-2 text-ink-2">
                  <Avatar name={lead.owner} />
                  {lead.owner}
                </div>
              </td>
              <td className="px-3 py-3">
                <StageBadge stage={lead.stage} />
              </td>
              <td className="tnum px-3 py-3 text-right font-medium">{formatUsd(lead.value)}</td>
              <td className="tnum py-3 pr-5 pl-3 text-right text-ink-2">{formatDate(lead.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
