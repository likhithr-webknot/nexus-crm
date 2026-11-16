"use client";

import { Dialog, DialogBody } from "@/components/ui/dialog";
import { CircleHelp } from "lucide-react";
import { useState } from "react";

const SHORTCUTS: [keys: string[], what: string][] = [
  [["⌘", "K"], "Jump to search (Ctrl K on Windows and Linux)"],
  [["←", "→"], "Move the focused deal to the previous or next stage"],
  [["Esc"], "Close a dialog or menu"],
  [["Tab"], "Move to the next control"],
];

/** The help button, and the list of keyboard shortcuts it opens. */
export function ShortcutsHelp() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button aria-label="Help" onClick={() => setOpen(true)} className="hidden size-9 place-items-center rounded-lg text-ink-2 hover:bg-canvas sm:grid">
        <CircleHelp className="size-[18px]" />
      </button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Keyboard shortcuts" size="sm">
        <DialogBody>
          <dl className="divide-y divide-line text-[13px]">
            {SHORTCUTS.map(([keys, what]) => (
              <div key={what} className="flex items-center justify-between gap-4 py-2.5">
                <dt className="text-ink-2">{what}</dt>
                <dd className="flex shrink-0 gap-1">
                  {keys.map((k) => (
                    <kbd key={k} className="rounded border border-line bg-canvas px-1.5 py-0.5 font-mono text-[11px] text-ink-2">
                      {k}
                    </kbd>
                  ))}
                </dd>
              </div>
            ))}
          </dl>
        </DialogBody>
      </Dialog>
    </>
  );
}
