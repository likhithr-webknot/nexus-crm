import { Badge } from "@/components/ui/primitives";
import type { Tag } from "@/lib/tags";

/** A tag as a small coloured label. */
export function TagChip({ tag }: { tag: Tag }) {
  return <Badge tone={tag.color}>{tag.name}</Badge>;
}

/** The tags on a record, shortened to {@code max} with a "+N" for the rest (the full list is in the tooltip). */
export function TagList({ tags, max = 2 }: { tags: Tag[]; max?: number }) {
  if (tags.length === 0) return null;
  const shown = tags.slice(0, max);
  const hidden = tags.slice(max);
  return (
    <span className="flex flex-wrap items-center gap-1">
      {shown.map((t) => (
        <TagChip key={t.id} tag={t} />
      ))}
      {hidden.length > 0 && (
        <span title={hidden.map((t) => t.name).join(", ")} className="tnum text-[12px] text-ink-3">
          +{hidden.length}
        </span>
      )}
    </span>
  );
}
