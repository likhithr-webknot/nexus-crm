import { EmptyState, buttonSecondary } from "@/components/ui/primitives";
import { WifiOff } from "lucide-react";

/** Shown when a list or page could not be loaded. */
export function LoadFailed({ what, onRetry }: { what: string; onRetry: () => void }) {
  return (
    <EmptyState icon={WifiOff} title={`Couldn't load ${what}`} body="Check your connection, then try again.">
      <button onClick={onRetry} className={buttonSecondary}>
        Try again
      </button>
    </EmptyState>
  );
}

/** A quiet "loading" line inside a card. */
export function LoadingNote({ what }: { what: string }) {
  return (
    <p className="border-t border-line px-5 py-10 text-center text-[13px] text-ink-3" role="status">
      Loading {what}…
    </p>
  );
}
