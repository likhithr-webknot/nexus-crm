"use client";

import { useSession } from "@/components/auth/session";
import { Popover } from "@/components/ui/popover";
import { control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { cn } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import {
  createSavedView,
  deleteSavedView,
  listSavedViews,
  type SavedView,
  type ViewFilters,
  type ViewKind,
} from "@/lib/saved-views";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Bookmark, Trash2, Users } from "lucide-react";
import { useState } from "react";

/** Applies a saved set of filters, or saves the filters now on screen as a new view. */
export function SavedViewsMenu({
  kind,
  current,
  onApply,
}: {
  kind: ViewKind;
  /** The filters on screen now. Empty values are ignored. */
  current: ViewFilters;
  onApply: (filters: ViewFilters) => void;
}) {
  const session = useSession();
  const refresh = useRefresh();
  const views = useQuery({ queryKey: [...KEYS.savedViews, kind], queryFn: () => listSavedViews(kind) });
  const [name, setName] = useState("");
  const [shared, setShared] = useState(false);

  const filters = Object.fromEntries(Object.entries(current).filter(([, v]) => v));
  const save = useMutation({
    mutationFn: () => createSavedView({ entityType: kind, name: name.trim(), filters, shared }),
    onSuccess: (view) => {
      toast(`View “${view.name}” saved.`);
      setName("");
      setShared(false);
      void refresh([KEYS.savedViews]);
    },
  });
  const remove = useMutation({
    mutationFn: (view: SavedView) => deleteSavedView(view.id),
    onSuccess: () => void refresh([KEYS.savedViews]),
  });
  const error = save.isError ? errorMessage(save.error) : remove.isError ? errorMessage(remove.error) : null;

  return (
    <Popover
      label="Saved views"
      className="w-72 p-3"
      trigger={
        <span className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-ink-2")}>
          <Bookmark className="size-4" aria-hidden />
          Views
        </span>
      }
    >
      {(close) => (
        <div className="space-y-3">
          {views.isPending ? (
            <p className="text-[12.5px] text-ink-3">Loading views…</p>
          ) : views.isError ? (
            <p className="text-[12.5px] text-down">Saved views could not be loaded.</p>
          ) : views.data.length === 0 ? (
            <p className="text-[12.5px] text-ink-3">
              No saved views yet. Set up the filters you want, then save them below.
            </p>
          ) : (
            <ul className="max-h-48 space-y-0.5 overflow-y-auto">
              {views.data.map((v) => (
                <li key={v.id} className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => {
                      onApply(v.filters);
                      close();
                    }}
                    className="flex min-w-0 flex-1 items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] text-ink hover:bg-canvas"
                  >
                    <span className="truncate">{v.name}</span>
                    {v.shared && <Users className="size-3.5 shrink-0 text-ink-3" aria-label="Shared with the team" />}
                  </button>
                  {(v.mine || session.role === "ADMIN") && (
                    <button
                      type="button"
                      aria-label={`Delete view ${v.name}`}
                      onClick={() => remove.mutate(v)}
                      className="grid size-7 place-items-center rounded-md text-ink-3 hover:bg-canvas hover:text-down"
                    >
                      <Trash2 className="size-3.5" aria-hidden />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (name.trim()) save.mutate();
            }}
            className="space-y-2 border-t border-line pt-3"
          >
            <label className="block text-[12px] font-medium text-ink-2">
              Save current filters as
              <input
                value={name}
                maxLength={60}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Qualified this month"
                className={cn(control, "mt-1.5 w-full px-2.5 font-normal")}
              />
            </label>
            <label className="flex items-center gap-2 text-[12.5px] text-ink-2">
              <input
                type="checkbox"
                checked={shared}
                onChange={(e) => setShared(e.target.checked)}
                className="accent-brand"
              />
              Share with the team
            </label>
            <button
              type="submit"
              disabled={!name.trim() || save.isPending}
              className="h-8 w-full rounded-md bg-brand px-2 text-[12.5px] font-medium text-white disabled:opacity-50"
            >
              {save.isPending ? "Saving…" : "Save view"}
            </button>
          </form>
          {error && (
            <p role="alert" className="text-[12px] text-down">
              {error}
            </p>
          )}
        </div>
      )}
    </Popover>
  );
}
