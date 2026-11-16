import { useCurrency } from "@/components/currency/currency-provider";
import { Avatar, StageBadge } from "@/components/ui/primitives";
import { formatDate } from "@/lib/format";
import type { Lead } from "@/lib/leads";
import { moneyValue } from "@/lib/money";
import { sourceLabel } from "@/lib/stages";
import { MoreHorizontal } from "lucide-react";

const COLUMNS = ["Lead", "Contact", "Source", "Assigned to", "Stage"];

export function LeadTable({
  rows,
  selected,
  allOnPage,
  someOnPage,
  onToggle,
  onAct,
}: {
  rows: Lead[];
  selected: ReadonlyMap<string, Lead>;
  allOnPage: boolean;
  someOnPage: boolean;
  onToggle: (leads: Lead[], on: boolean) => void;
  onAct: (lead: Lead) => void;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[1000px] text-left text-[13px]">
        <thead>
          <tr className="border-y border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
            <th className="w-11 py-2.5 pl-5">
              <input
                type="checkbox"
                aria-label="Select all leads on this page"
                className="block size-4 accent-brand"
                checked={allOnPage}
                ref={(el) => {
                  if (el) el.indeterminate = someOnPage;
                }}
                onChange={(e) => onToggle(rows, e.target.checked)}
              />
            </th>
            {COLUMNS.map((h) => (
              <th key={h} className="px-3 py-2.5 font-medium">
                {h}
              </th>
            ))}
            <th className="px-3 py-2.5 text-right font-medium">Estimated value</th>
            <th className="py-2.5 pr-3 pl-3 text-right font-medium">Created</th>
            <th className="w-12 py-2.5 pr-4">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((lead) => (
            <LeadRow key={lead.id} lead={lead} selected={selected.has(lead.id)} onToggle={onToggle} onAct={onAct} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LeadRow({ lead, selected, onToggle, onAct }: { lead: Lead; selected: boolean; onToggle: (leads: Lead[], on: boolean) => void; onAct: (lead: Lead) => void }) {
  const { format } = useCurrency();
  const email = lead.email ?? "";
  const phone = lead.phone ?? "";
  return (
    <tr className={selected ? "bg-brand-soft/50" : "hover:bg-canvas/60"}>
      <td className="py-3 pl-5">
        <input type="checkbox" aria-label={`Select ${lead.name}`} className="block size-4 accent-brand" checked={selected} onChange={(e) => onToggle([lead], e.target.checked)} />
      </td>
      <td className="px-3 py-3">
        <div className="flex items-center gap-3">
          <Avatar name={lead.name} />
          <div className="min-w-0">
            <p className="font-medium whitespace-nowrap text-ink">{lead.name}</p>
            <p className="text-[12.5px] whitespace-nowrap text-ink-3">{lead.companyName}</p>
          </div>
        </div>
      </td>
      <td className="px-3 py-3">
        {email && (
          <a href={`mailto:${email}`} className="block text-ink-2 hover:text-brand hover:underline">
            {email}
          </a>
        )}
        {phone && (
          <a href={`tel:${phone.replace(/[^+\d]/g, "")}`} className="tnum block text-[12.5px] whitespace-nowrap text-ink-3 hover:text-brand hover:underline">
            {phone}
          </a>
        )}
      </td>
      <td className="px-3 py-3 whitespace-nowrap text-ink-2">{sourceLabel(lead.source)}</td>
      <td className="px-3 py-3 whitespace-nowrap">
        {lead.owner ? (
          <div className="flex items-center gap-2 text-ink-2">
            <Avatar name={lead.owner.name} />
            {lead.owner.name}
          </div>
        ) : (
          <span className="text-ink-3">Unassigned</span>
        )}
      </td>
      <td className="px-3 py-3">
        <StageBadge stage={lead.status} />
      </td>
      <td className="tnum px-3 py-3 text-right font-medium whitespace-nowrap text-ink-2">
        {lead.estimatedValue ? format(moneyValue(lead.estimatedValue), lead.estimatedValue.currency) : "—"}
      </td>
      <td className="tnum py-3 pr-3 pl-3 text-right whitespace-nowrap text-ink-2">{formatDate(lead.createdAt)}</td>
      <td className="py-3 pr-4 text-right">
        <button aria-label={`Actions for ${lead.name}`} onClick={() => onAct(lead)} className="grid size-8 place-items-center rounded-md text-ink-3 hover:bg-canvas hover:text-ink">
          <MoreHorizontal className="size-4" aria-hidden />
        </button>
      </td>
    </tr>
  );
}
