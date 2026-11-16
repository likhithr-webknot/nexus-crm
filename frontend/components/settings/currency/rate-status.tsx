import { useCurrency } from "@/components/currency/currency-provider";
import { buttonSecondary } from "@/components/ui/primitives";
import { RefreshCw } from "lucide-react";

/** One line saying whether the exchange rates are loaded, fresh, old, or failed. */
export function RateStatus() {
  const { rateState, refreshRates } = useCurrency();

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-canvas px-3.5 py-3 text-[12.5px] text-ink-2" role="status" aria-live="polite">
      {rateState.status === "loading" && "Loading daily exchange rates…"}
      {rateState.status === "ready" && (
        <span className={rateState.stale ? "text-amber-800" : undefined}>
          {rateState.stale
            ? `Some exchange rates are more than 7 days old (newest: ${rateState.updatedAt}). Converted totals may be out of date.`
            : `Exchange rates last updated ${rateState.updatedAt}. The server downloads new rates every day.`}
        </span>
      )}
      {rateState.status === "error" && (
        <>
          <span className="text-down">Could not load exchange rates. Converted totals are unavailable.</span>
          <button onClick={refreshRates} className={buttonSecondary}>
            <RefreshCw className="size-3.5" aria-hidden />
            Retry
          </button>
        </>
      )}
    </div>
  );
}
