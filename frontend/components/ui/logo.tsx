import { cn } from "@/lib/format";

/*
 * The mark is an N drawn as one route between two points: a lead on one end,
 * a closed deal on the other. app/icon.svg is the same drawing for the tab icon.
 */
function Mark({ label }: { label?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      className="size-8 shrink-0"
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <rect width="32" height="32" rx="8" className="fill-brand" />
      <path
        d="M10.5 22V10.5l11 11V10"
        fill="none"
        stroke="#fff"
        strokeWidth={2.6}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="10.5" cy="22" r="2.7" fill="#fff" />
      <circle cx="21.5" cy="10" r="2.7" fill="#a5b4fc" />
    </svg>
  );
}

/** Brand mark plus wordmark. `tone` is the background it sits on. */
export function Logo({ tone = "light", wordmark = true }: { tone?: "light" | "dark"; wordmark?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <Mark label={wordmark ? undefined : "Nexus CRM"} />
      {wordmark && (
        <span className="flex items-center gap-2">
          <span className={cn("text-[17px] font-semibold tracking-[-0.03em]", tone === "dark" ? "text-white" : "text-ink")}>
            Nexus
          </span>
          <span
            className={cn(
              "rounded-[5px] px-1.5 py-px text-[10px] font-semibold tracking-[0.08em]",
              tone === "dark" ? "bg-white/10 text-indigo-200" : "bg-brand-soft text-brand-ink",
            )}
          >
            CRM
          </span>
        </span>
      )}
    </span>
  );
}
