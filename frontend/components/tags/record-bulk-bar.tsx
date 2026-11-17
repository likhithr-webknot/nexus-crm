"use client";

import { TagPicker } from "@/components/tags/tag-picker";
import { ConfirmDialog } from "@/components/ui/dialog";
import { control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { cn } from "@/lib/format";
import { AFTER, KEYS } from "@/lib/query-keys";
import { deleteRecords, type TagKind } from "@/lib/tags";
import { useMutation } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { useState } from "react";

/** Tag or delete the ticked contacts or deals. Both are all-or-nothing on the server. */
export function RecordBulkBar({
  kind,
  ids,
  onClear,
}: {
  kind: Exclude<TagKind, "leads">;
  ids: string[];
  onClear: () => void;
}) {
  const refresh = useRefresh();
  const [confirming, setConfirming] = useState(false);
  const noun = kind === "contacts" ? "contact" : "deal";
  const remove = useMutation({
    mutationFn: () => deleteRecords(kind, ids),
    onSuccess: () => {
      toast(`${ids.length === 1 ? `1 ${noun}` : `${ids.length} ${noun}s`} deleted.`);
      void refresh(kind === "contacts" ? [...AFTER.contact, KEYS.companies] : AFTER.deal);
      setConfirming(false);
      onClear();
    },
  });

  return (
    <div
      role="toolbar"
      aria-label={`Actions for selected ${noun}s`}
      className="flex flex-wrap items-center gap-2 border-t border-line bg-brand-soft/50 px-4 py-2.5"
    >
      <p className="tnum mr-1 text-[12.5px] font-medium text-ink" aria-live="polite">
        {ids.length} selected
      </p>
      <TagPicker kind={kind} ids={ids} onDone={onClear} />
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-down")}
      >
        <Trash2 className="size-4" aria-hidden />
        Delete
      </button>
      <button type="button" onClick={onClear} className="ml-auto text-[12.5px] font-medium text-brand hover:underline">
        Clear selection
      </button>
      <ConfirmDialog
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => remove.mutate()}
        pending={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        title={ids.length === 1 ? `Delete this ${noun}?` : `Delete ${ids.length} ${noun}s?`}
        body="They are moved to the recycle bin. A manager or admin can restore them from there for 30 days."
        confirmLabel="Delete"
      />
    </div>
  );
}
