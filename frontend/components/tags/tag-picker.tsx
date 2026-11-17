"use client";

import { Popover } from "@/components/ui/popover";
import { control } from "@/components/ui/primitives";
import { useChangeTags, useCreateTag, useTags } from "@/hooks/use-tags";
import { errorMessage } from "@/lib/api";
import { cn } from "@/lib/format";
import type { TagKind } from "@/lib/tags";
import { Tag as TagIcon } from "lucide-react";
import { useState } from "react";
import { TagChip } from "./tag-chip";

/** Adds a tag to, or removes it from, the selected records. The user can also make a new tag here. */
export function TagPicker({ kind, ids, onDone }: { kind: TagKind; ids: string[]; onDone?: () => void }) {
  const tags = useTags();
  const create = useCreateTag();
  const change = useChangeTags(kind, onDone);
  const [chosen, setChosen] = useState("");
  const [name, setName] = useState("");

  const apply = (action: "add" | "remove", close: () => void) => {
    change.mutate({ ids, [action]: [chosen] }, { onSuccess: close });
  };
  const makeTag = () =>
    create.mutate({ name }, { onSuccess: (tag) => { setChosen(tag.id); setName(""); } });

  const error = change.isError ? errorMessage(change.error) : create.isError ? errorMessage(create.error) : null;

  return (
    <Popover
      label="Tag selected records"
      align="start"
      className="w-72 p-3"
      trigger={
        <span className={cn(control, "inline-flex items-center gap-2 px-3 font-medium text-ink-2")}>
          <TagIcon className="size-4" aria-hidden />
          Tag
        </span>
      }
    >
      {(close) => (
        <div className="space-y-3">
          {tags.isPending ? (
            <p className="text-[12.5px] text-ink-3">Loading tags…</p>
          ) : tags.isError ? (
            <p className="text-[12.5px] text-down">Tags could not be loaded.</p>
          ) : tags.data.length === 0 ? (
            <p className="text-[12.5px] text-ink-3">No tags yet. Create the first one below.</p>
          ) : (
            <ul role="listbox" aria-label="Tags" className="max-h-44 space-y-1 overflow-y-auto">
              {tags.data.map((t) => (
                <li key={t.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={chosen === t.id}
                    onClick={() => setChosen(t.id)}
                    className={cn("flex w-full items-center rounded-md px-2 py-1.5 text-left hover:bg-canvas", chosen === t.id && "bg-brand-soft")}
                  >
                    <TagChip tag={t} />
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="flex gap-2">
            <button
              type="button"
              disabled={!chosen || change.isPending}
              onClick={() => apply("add", close)}
              className="h-8 flex-1 rounded-md bg-brand px-2 text-[12.5px] font-medium text-white disabled:opacity-50"
            >
              Add to {ids.length}
            </button>
            <button
              type="button"
              disabled={!chosen || change.isPending}
              onClick={() => apply("remove", close)}
              className="h-8 flex-1 rounded-md border border-line px-2 text-[12.5px] font-medium text-ink-2 hover:bg-canvas disabled:opacity-50"
            >
              Remove
            </button>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (name.trim()) makeTag();
            }}
            className="flex gap-2 border-t border-line pt-3"
          >
            <label className="flex-1">
              <span className="sr-only">New tag name</span>
              <input
                value={name}
                maxLength={40}
                onChange={(e) => setName(e.target.value)}
                placeholder="New tag…"
                className={cn(control, "w-full px-2.5")}
              />
            </label>
            <button type="submit" disabled={!name.trim() || create.isPending} className="h-9 rounded-md border border-line px-2.5 text-[12.5px] font-medium text-ink-2 hover:bg-canvas disabled:opacity-50">
              Create
            </button>
          </form>
          {error && <p role="alert" className="text-[12px] text-down">{error}</p>}
        </div>
      )}
    </Popover>
  );
}
