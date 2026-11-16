"use client";

import { ConfirmDialog } from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api";
import { useMutation } from "@tanstack/react-query";

/**
 * Asks before deleting something, then deletes it. The API refuses some deletes (a company that still has
 * contacts, for example), and its reason is shown in the dialog.
 */
export function DeleteDialog<T>({
  target,
  title,
  body,
  successMessage,
  action,
  onClose,
  onDeleted,
}: {
  target: T | null;
  title: (target: T) => string;
  body: string;
  successMessage: (target: T) => string;
  action: (target: T) => Promise<unknown>;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const remove = useMutation({
    mutationFn: action,
    onSuccess: (_, deleted) => {
      toast(successMessage(deleted));
      onDeleted();
      onClose();
    },
  });
  const close = () => {
    remove.reset();
    onClose();
  };

  return (
    <ConfirmDialog
      open={target !== null}
      onClose={close}
      onConfirm={() => target && remove.mutate(target)}
      pending={remove.isPending}
      error={remove.isError ? errorMessage(remove.error) : null}
      title={target ? title(target) : ""}
      body={body}
      confirmLabel="Delete"
    />
  );
}
