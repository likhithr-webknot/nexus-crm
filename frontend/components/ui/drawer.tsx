"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/lib/format";

const WIDTHS = { md: "max-w-[560px]", lg: "max-w-[760px]", xl: "max-w-[min(1180px,94vw)]" };

/** True when the event happened in something layered above the drawer: a dialog, a menu, a toast. */
function inLayerAbove(target: EventTarget | null) {
  return target instanceof Element && Boolean(target.closest('dialog, [role="dialog"], [role="menu"], [role="listbox"], [role="status"]'));
}

/**
 * A panel that slides in from the right and keeps the page behind it in view.
 * Used for looking at or editing one record without leaving its list.
 *
 * It is not a focus trap on purpose: forms and confirmations open on top of it as their own
 * dialogs, and two traps would fight over the keyboard.
 */
export function Drawer({
  open,
  onClose,
  title,
  subtitle,
  size = "md",
  actions,
  footer,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: React.ReactNode;
  size?: keyof typeof WIDTHS;
  /** Buttons beside the close button. */
  actions?: React.ReactNode;
  /** Pinned to the bottom, for a form's Save and Cancel. */
  footer?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Dialog.Root open={open} modal={false} onOpenChange={(next) => !next && onClose()}>
      <Dialog.Portal>
        <div
          aria-hidden
          onClick={onClose}
          className={cn("fixed inset-0 z-40 bg-scrim", open ? "animate-[fade-in_var(--duration-dialog)_ease-out]" : "animate-[fade-out_var(--duration-menu)_var(--ease-exit)]")}
        />
        <Dialog.Content
          aria-describedby={undefined}
          // A dialog opened from inside the drawer handles its own Escape and clicks.
          onEscapeKeyDown={(event) => document.querySelector("dialog[open]") && event.preventDefault()}
          onInteractOutside={(event) => inLayerAbove(event.target) && event.preventDefault()}
          className={cn(
            "fixed inset-y-0 right-0 z-40 flex w-full flex-col border-l border-line bg-canvas text-ink shadow-[-24px_0_64px_-32px_rgba(15,23,42,.45)] outline-none",
            "data-[state=closed]:animate-[panel-out_var(--duration-dialog)_var(--ease-exit)] data-[state=open]:animate-[panel-in_var(--duration-drawer)_var(--ease-enter)]",
            WIDTHS[size],
          )}
        >
          <header className="flex shrink-0 items-start gap-3 border-b border-line bg-surface px-5 py-4">
            <div className="min-w-0 flex-1">
              <Dialog.Title className="truncate text-[16px] font-semibold tracking-[-0.01em]">{title}</Dialog.Title>
              {subtitle && <div className="mt-0.5 truncate text-[12.5px] text-ink-3">{subtitle}</div>}
            </div>
            {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
            <Dialog.Close aria-label="Close" className="-mr-1.5 grid size-8 shrink-0 place-items-center rounded-md text-ink-3 hover:bg-surface-muted hover:text-ink">
              <X className="size-4" aria-hidden />
            </Dialog.Close>
          </header>
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto p-5">{children}</div>
          {footer && <footer className="flex shrink-0 justify-end gap-2 border-t border-line bg-surface px-5 py-3.5">{footer}</footer>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
