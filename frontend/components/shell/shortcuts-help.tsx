"use client";

import { Dialog, DialogBody } from "@/components/ui/dialog";
import { Tooltip } from "@/components/ui/tooltip";
import { CREATE, GO_TO, SHELL_EVENTS, useModKey } from "@/lib/shortcuts";
import { CircleHelp } from "lucide-react";
import { useEffect, useState } from "react";
import { topbarIconButton } from "./topbar";

type Row = { keys: string[]; what: string };

/** The help button, and the list of keyboard shortcuts it opens. "?" opens it from anywhere. */
export function ShortcutsHelp() {
  const [open, setOpen] = useState(false);
  const mod = useModKey();

  useEffect(() => {
    const show = () => setOpen(true);
    window.addEventListener(SHELL_EVENTS.shortcuts, show);
    return () => window.removeEventListener(SHELL_EVENTS.shortcuts, show);
  }, []);

  const groups: { title: string; rows: Row[] }[] = [
    {
      title: "General",
      rows: [
        { keys: [mod, "K"], what: "Search and commands" },
        { keys: ["/"], what: "Search" },
        { keys: ["["], what: "Collapse or expand the sidebar" },
        { keys: ["?"], what: "Show this list" },
        { keys: ["Esc"], what: "Close a dialog, drawer or menu" },
      ],
    },
    { title: "Go to", rows: Object.entries(GO_TO).map(([key, { label }]) => ({ keys: ["G", key.toUpperCase()], what: label })) },
    { title: "Create", rows: Object.entries(CREATE).map(([key, { label }]) => ({ keys: ["N", key.toUpperCase()], what: `New ${label.toLowerCase()}` })) },
    { title: "Pipeline", rows: [{ keys: ["←", "→"], what: "Move the focused deal to the previous or next stage" }] },
  ];

  return (
    <>
      <Tooltip label="Keyboard shortcuts" keys={["?"]}>
        <button aria-label="Keyboard shortcuts" onClick={() => setOpen(true)} className={`${topbarIconButton} hidden sm:grid`}>
          <CircleHelp className="size-[17px]" aria-hidden />
        </button>
      </Tooltip>
      <Dialog open={open} onClose={() => setOpen(false)} title="Keyboard shortcuts" description="Two-key shortcuts are pressed one after the other, not together." size="lg">
        <DialogBody>
          <div className="grid gap-x-10 gap-y-6 sm:grid-cols-2">
            {groups.map(({ title, rows }) => (
              <section key={title}>
                <h3 className="text-[11px] font-medium tracking-[0.04em] text-ink-3 uppercase">{title}</h3>
                <dl className="mt-1.5 text-[13px]">
                  {rows.map(({ keys, what }) => (
                    <div key={what} className="flex items-center justify-between gap-4 py-1.5">
                      <dt className="text-ink-2">{what}</dt>
                      <dd className="flex shrink-0 items-center gap-1">
                        {keys.map((key, index) => (
                          <span key={key} className="flex items-center gap-1">
                            {index > 0 && keys[0].length === 1 && <span className="text-[11px] text-ink-3">then</span>}
                            <kbd className="kbd">{key}</kbd>
                          </span>
                        ))}
                      </dd>
                    </div>
                  ))}
                </dl>
              </section>
            ))}
          </div>
        </DialogBody>
      </Dialog>
    </>
  );
}
