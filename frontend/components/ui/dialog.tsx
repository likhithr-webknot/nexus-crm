"use client";

import { useEffect, useId, useRef } from "react";
import { LoaderCircle, X } from "lucide-react";
import { buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { cn } from "@/lib/format";

/**
 * A modal dialog on the browser's own <dialog>. That gives us, for free:
 * focus kept inside, Escape to close, the page behind made inert, and focus
 * returned to the button that opened it.
 *
 * The content only exists while the dialog is open, so a form inside starts
 * empty every time.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  size = "md",
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: React.ReactNode;
  size?: "sm" | "md" | "lg";
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
      // Start in the first field. Without one (a confirmation), start on Cancel, the safe choice.
      const first = dialog.querySelector<HTMLElement>("input:not([type=file]), select, textarea, footer button");
      first?.focus();
    }
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      // Fires for Escape as well as for our own close().
      onClose={onClose}
      // The dialog element itself is only hit when the click lands on the backdrop.
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      className={cn(
        "m-auto w-[calc(100%-2rem)] rounded-xl border border-line bg-surface p-0 text-ink shadow-[0_24px_64px_-16px_rgba(15,23,42,0.35)] backdrop:bg-slate-950/45",
        size === "sm" ? "max-w-sm" : size === "md" ? "max-w-lg" : "max-w-2xl",
      )}
    >
      {open && (
        <>
          <header className="flex items-start gap-4 px-5 pt-5">
            <div className="min-w-0 flex-1">
              <h2 id={titleId} className="text-[16px] font-semibold tracking-[-0.01em]">
                {title}
              </h2>
              {description && <p className="mt-1 text-[13px] text-ink-2">{description}</p>}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close"
              className="-mt-1 -mr-1.5 rounded-md p-1.5 text-ink-3 hover:bg-canvas hover:text-ink"
            >
              <X className="size-4" aria-hidden />
            </button>
          </header>
          {children}
        </>
      )}
    </dialog>
  );
}

/** The body of a dialog: its fields or text. */
export function DialogBody({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn("grid gap-4 px-5 py-4", className)}>{children}</div>;
}

/** Cancel on the left, the main action on the right. The main action submits the surrounding form. */
export function DialogActions({
  onCancel,
  submitLabel,
  pending,
  disabled,
  danger,
  onSubmit,
}: {
  onCancel: () => void;
  submitLabel: string;
  pending?: boolean;
  disabled?: boolean;
  danger?: boolean;
  /** For dialogs without a form. Leave out to submit the form instead. */
  onSubmit?: () => void;
}) {
  return (
    <footer className="flex justify-end gap-2 border-t border-line bg-canvas/50 px-5 py-3.5">
      <button type="button" onClick={onCancel} disabled={pending} className={buttonSecondary}>
        Cancel
      </button>
      <button
        type={onSubmit ? "button" : "submit"}
        onClick={onSubmit}
        disabled={pending || disabled}
        className={cn(buttonPrimary, danger && "bg-rose-600 hover:bg-rose-700 disabled:hover:bg-rose-600")}
      >
        {pending && <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />}
        {submitLabel}
      </button>
    </footer>
  );
}

/** Asks before something that cannot be undone. */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  body,
  confirmLabel,
  pending,
  error,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  body: React.ReactNode;
  confirmLabel: string;
  pending?: boolean;
  error?: string | null;
}) {
  return (
    <Dialog open={open} onClose={onClose} title={title} size="sm">
      <DialogBody>
        <p className="text-[13.5px] text-ink-2">{body}</p>
        {error && <FormError>{error}</FormError>}
      </DialogBody>
      <DialogActions onCancel={onClose} onSubmit={onConfirm} submitLabel={confirmLabel} pending={pending} danger />
    </Dialog>
  );
}

/** A message at the bottom of a form when saving failed. */
export function FormError({ children }: { children: React.ReactNode }) {
  return (
    <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-[13px] text-rose-700 ring-1 ring-rose-200 ring-inset">
      {children}
    </p>
  );
}
