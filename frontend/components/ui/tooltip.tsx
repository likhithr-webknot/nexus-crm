"use client";

import * as RadixTooltip from "@radix-ui/react-tooltip";

/** Wraps the app once so tooltips share one delay: the first waits, the next ones appear at once. */
export function TooltipProvider({ children }: { children: React.ReactNode }) {
  return (
    <RadixTooltip.Provider delayDuration={400} skipDelayDuration={200}>
      {children}
    </RadixTooltip.Provider>
  );
}

/**
 * A short label shown on hover and keyboard focus. The child must be a single element (a button or link).
 * `disabled` keeps the child mounted and only stops the label from showing.
 */
export function Tooltip({
  label,
  keys,
  side = "bottom",
  disabled,
  children,
}: {
  label: string;
  /** Key caps shown after the label, for example ["G", "L"]. */
  keys?: string[];
  side?: "top" | "right" | "bottom" | "left";
  disabled?: boolean;
  children: React.ReactElement;
}) {
  return (
    <RadixTooltip.Root>
      <RadixTooltip.Trigger asChild>{children}</RadixTooltip.Trigger>
      {!disabled && (
        <RadixTooltip.Portal>
          <RadixTooltip.Content
            side={side}
            sideOffset={8}
            collisionPadding={8}
            className="z-[70] flex origin-[var(--radix-tooltip-content-transform-origin)] items-center gap-2 rounded-md bg-ink px-2 py-1 text-[11.5px] font-medium text-surface shadow-[0_6px_20px_-6px_rgba(0,0,0,.35)] animate-[tip-in_var(--duration-hover)_var(--ease-enter)]"
          >
            {label}
            {keys && (
              <span className="flex gap-0.5 opacity-70" aria-hidden>
                {keys.map((key) => (
                  <kbd key={key} className="font-sans text-[10.5px]">
                    {key}
                  </kbd>
                ))}
              </span>
            )}
          </RadixTooltip.Content>
        </RadixTooltip.Portal>
      )}
    </RadixTooltip.Root>
  );
}
