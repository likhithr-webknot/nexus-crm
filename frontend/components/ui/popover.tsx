"use client";

import { useEffect, useId, useRef, useState } from "react";
import { cn } from "@/lib/format";

/**
 * Minimal disclosure popover: click to toggle, closes on outside click and
 * Escape (returning focus to the trigger). Enough for header menus without
 * pulling in a headless-UI dependency.
 */
export function Popover({
  trigger,
  label,
  align = "end",
  className,
  children,
}: {
  trigger: React.ReactNode;
  label: string;
  align?: "start" | "end";
  className?: string;
  children: React.ReactNode | ((close: () => void) => React.ReactNode);
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const close = () => setOpen(false);

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
        className="flex items-center rounded-lg hover:bg-canvas"
      >
        {trigger}
      </button>
      {open && (
        <div
          id={id}
          className={cn(
            "absolute top-full z-40 mt-2 rounded-xl border border-line bg-surface shadow-[0_12px_32px_-8px_rgba(15,23,42,0.18)]",
            align === "end" ? "right-0" : "left-0",
            className,
          )}
        >
          {typeof children === "function" ? children(close) : children}
        </div>
      )}
    </div>
  );
}
