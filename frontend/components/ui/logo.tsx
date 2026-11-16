import { cn } from "@/lib/format";

/** Brand mark plus wordmark. `tone` is the background it sits on. */
export function Logo({
  tone = "light",
  wordmark = true,
}: {
  tone?: "light" | "dark";
  wordmark?: boolean;
}) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <span
        aria-hidden={wordmark}
        aria-label={wordmark ? undefined : "Nexus CRM"}
        role={wordmark ? undefined : "img"}
        className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand text-[15px] font-bold text-white"
      >
        N
      </span>
      {wordmark && (
        <span
          className={cn(
            "text-[17px] font-semibold tracking-[-0.02em]",
            tone === "dark" ? "text-white" : "text-ink",
          )}
        >
          Nexus
          <span
            className={cn(
              "ml-1 text-[11px] font-medium tracking-wider",
              tone === "dark" ? "text-nav-ink" : "text-ink-3",
            )}
          >
            CRM
          </span>
        </span>
      )}
    </span>
  );
}
