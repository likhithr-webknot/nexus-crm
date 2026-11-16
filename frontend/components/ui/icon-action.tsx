import { cn } from "@/lib/format";
import { Pencil, Trash2, type LucideIcon } from "lucide-react";

/** A small icon-only button for a row: its meaning is in the label, which screen readers and tooltips use. */
export function IconAction({ icon: Icon, label, onClick, danger, disabled }: { icon: LucideIcon; label: string; onClick: () => void; danger?: boolean; disabled?: boolean }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "grid size-8 shrink-0 place-items-center rounded-md text-ink-3 transition-colors disabled:opacity-50",
        danger ? "hover:bg-rose-50 hover:text-down" : "hover:bg-canvas hover:text-ink",
      )}
    >
      <Icon className="size-4" aria-hidden />
    </button>
  );
}

/** The usual pair: edit and delete. */
export function EditDeleteActions({ name, onEdit, onDelete }: { name: string; onEdit: () => void; onDelete: () => void }) {
  return (
    <span className="flex shrink-0 items-center">
      <IconAction icon={Pencil} label={`Edit ${name}`} onClick={onEdit} />
      <IconAction icon={Trash2} label={`Delete ${name}`} onClick={onDelete} danger />
    </span>
  );
}
