"use client";

import { useSession } from "@/components/auth/session";
import { Popover } from "@/components/ui/popover";
import { control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { TagPicker } from "@/components/tags/tag-picker";
import { useAssignableUsers } from "@/hooks/use-people";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { cn } from "@/lib/format";
import { assignLeads, changeLeadsStatus } from "@/lib/leads";
import { AFTER } from "@/lib/query-keys";
import { stageLabel, type LeadStatus } from "@/lib/stages";
import { useMutation } from "@tanstack/react-query";
import { Trash2, UserRound } from "lucide-react";
import { useState } from "react";

const STATUSES: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "DISQUALIFIED"];
const UNASSIGNED = "unassigned";

/**
 * What can be done to the ticked leads in one go. Every action is all-or-nothing on the server: if one lead cannot take
 * the change, none of them change and the message says why.
 */
export function LeadBulkBar({ ids, onClear, onDelete }: { ids: string[]; onClear: () => void; onDelete: () => void }) {
  const session = useSession();
  const refresh = useRefresh();
  const people = useAssignableUsers(session.role !== "REP");
  const [owner, setOwner] = useState("");
  const [status, setStatus] = useState<LeadStatus>("CONTACTED");
  const [reason, setReason] = useState("");

  const done = (what: string) => (r: { count: number }) => {
    toast(`${r.count === 1 ? "1 lead" : `${r.count} leads`} ${what}.`);
    void refresh(AFTER.lead);
    onClear();
  };
  const assign = useMutation({
    mutationFn: () => assignLeads(ids, owner === UNASSIGNED ? null : owner),
    onSuccess: done("reassigned"),
  });
  const move = useMutation({
    mutationFn: () => changeLeadsStatus(ids, status, status === "DISQUALIFIED" ? reason.trim() : undefined),
    onSuccess: done("updated"),
  });
  const error = assign.isError ? errorMessage(assign.error) : move.isError ? errorMessage(move.error) : null;

  return (
    <div
      role="toolbar"
      aria-label="Actions for selected leads"
      className="flex flex-wrap items-center gap-2 border-t border-line bg-brand-soft/50 px-4 py-2.5"
    >
      <p className="tnum mr-1 text-[12.5px] font-medium text-ink" aria-live="polite">
        {ids.length} selected
      </p>

      <TagPicker kind="leads" ids={ids} onDone={onClear} />

      {session.role !== "REP" && (
        <Popover
          label="Assign selected leads"
          align="start"
          className="w-64 p-3"
          trigger={
            <span className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-ink-2")}>
              <UserRound className="size-4" aria-hidden />
              Assign
            </span>
          }
        >
          <div className="space-y-2">
            <label className="block text-[12px] font-medium text-ink-2">
              Give {ids.length === 1 ? "this lead" : `these ${ids.length} leads`} to
              <select
                value={owner}
                onChange={(e) => setOwner(e.target.value)}
                className={cn(control, "mt-1.5 w-full px-2.5 font-normal")}
              >
                <option value="">Choose a person…</option>
                <option value={UNASSIGNED}>Nobody (unassigned)</option>
                {(people.data ?? []).map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.displayName}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              disabled={!owner || assign.isPending}
              onClick={() => assign.mutate()}
              className="h-8 w-full rounded-md bg-brand px-2 text-[12.5px] font-medium text-white disabled:opacity-50"
            >
              {assign.isPending ? "Assigning…" : "Assign"}
            </button>
          </div>
        </Popover>
      )}

      <Popover
        label="Change status of selected leads"
        align="start"
        className="w-64 p-3"
        trigger={
          <span className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-ink-2")}>Status</span>
        }
      >
        <div className="space-y-2">
          <label className="block text-[12px] font-medium text-ink-2">
            Move to
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as LeadStatus)}
              className={cn(control, "mt-1.5 w-full px-2.5 font-normal")}
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {stageLabel(s)}
                </option>
              ))}
            </select>
          </label>
          {status === "DISQUALIFIED" && (
            <label className="block text-[12px] font-medium text-ink-2">
              Reason
              <input
                value={reason}
                maxLength={500}
                onChange={(e) => setReason(e.target.value)}
                className={cn(control, "mt-1.5 w-full px-2.5 font-normal")}
              />
            </label>
          )}
          <button
            type="button"
            disabled={move.isPending || (status === "DISQUALIFIED" && !reason.trim())}
            onClick={() => move.mutate()}
            className="h-8 w-full rounded-md bg-brand px-2 text-[12.5px] font-medium text-white disabled:opacity-50"
          >
            {move.isPending ? "Updating…" : "Update status"}
          </button>
        </div>
      </Popover>

      <button
        type="button"
        onClick={onDelete}
        className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-down")}
      >
        <Trash2 className="size-4" aria-hidden />
        Delete
      </button>

      <button type="button" onClick={onClear} className="ml-auto text-[12.5px] font-medium text-brand hover:underline">
        Clear selection
      </button>

      {error && (
        <p role="alert" className="w-full text-[12.5px] text-down">
          {error}
        </p>
      )}
    </div>
  );
}
