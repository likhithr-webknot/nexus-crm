"use client";

import { useSyncExternalStore } from "react";
import { CircleCheck } from "lucide-react";

type Toast = { id: number; message: string };

const SHOW_MS = 5000;
let toasts: Toast[] = [];
let nextId = 1;
const listeners = new Set<() => void>();
const change = (next: Toast[]) => {
  toasts = next;
  listeners.forEach((l) => l());
};

/** Shows a short confirmation in the corner of the screen. Callable from anywhere. */
export function toast(message: string) {
  const id = nextId++;
  change([...toasts, { id, message }]);
  setTimeout(() => change(toasts.filter((t) => t.id !== id)), SHOW_MS);
}

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};
const none: Toast[] = [];

/** Renders the toasts. Mounted once, in the app shell. */
export function Toaster() {
  const shown = useSyncExternalStore(
    subscribe,
    () => toasts,
    () => none,
  );
  return (
    <div role="status" aria-live="polite" className="pointer-events-none fixed right-4 bottom-4 z-[60] flex flex-col items-end gap-2">
      {shown.map((t) => (
        <p
          key={t.id}
          className="flex max-w-sm items-start gap-2.5 rounded-lg bg-ink px-3.5 py-2.5 text-[13px] text-white shadow-[0_12px_32px_-8px_rgba(15,23,42,0.4)]"
        >
          <CircleCheck className="mt-px size-4 shrink-0 text-emerald-400" aria-hidden />
          {t.message}
        </p>
      ))}
    </div>
  );
}
