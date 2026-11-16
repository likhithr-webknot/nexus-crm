import type { ExchangeRate } from "@/lib/currencies";
import { cn } from "@/lib/format";

/** The latest rate of each currency. Admins can revert a rate that was typed in by hand. */
export function RatesTable({ rates, canRevert, reverting, onRevert }: { rates: ExchangeRate[]; canRevert: boolean; reverting: boolean; onRevert: (rate: ExchangeRate) => void }) {
  return (
    <div className="overflow-x-auto">
    <table className="w-full min-w-[420px] text-left text-[13px]">
      <thead>
        <tr className="border-b border-line text-[11.5px] tracking-wide text-ink-3 uppercase">
          <th className="py-2 font-medium">Currency</th>
          <th className="py-2 text-right font-medium">1 unit in INR</th>
          <th className="py-2 text-right font-medium">Rate date</th>
          <th className="py-2 text-right font-medium">Source</th>
          {canRevert && <th className="w-20 py-2" />}
        </tr>
      </thead>
      <tbody className="tnum divide-y divide-line">
        {rates.map((r) => (
          <tr key={r.currency}>
            <td className="py-2 font-medium text-ink">{r.currency}</td>
            <td className="py-2 text-right text-ink-2">{Number(r.rateToBase).toFixed(4)}</td>
            <td className={cn("py-2 text-right", r.stale ? "text-down" : "text-ink-2")}>{r.rateDate}</td>
            <td className="py-2 text-right text-ink-3">{r.source}</td>
            {canRevert && (
              <td className="py-2 text-right">
                {r.source === "MANUAL" && (
                  <button onClick={() => onRevert(r)} disabled={reverting} className="text-[12px] font-medium text-brand hover:underline">
                    Revert
                  </button>
                )}
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
    </div>
  );
}
