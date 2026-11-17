import { FormDialog } from "@/components/ui/dialog";
import { Field, fieldControl } from "@/components/ui/primitives";
import type { Deal } from "@/lib/deals";

/** Asks why a deal was lost before it is marked lost, because a lost deal needs a reason. */
export function LostDealDialog({
  deal,
  pending,
  onClose,
  onConfirm,
}: {
  deal: Deal | null;
  pending: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  return (
    <FormDialog
      open={deal !== null}
      onClose={onClose}
      title="Mark deal as lost"
      description={deal ? `${deal.company.name} · ${deal.name}` : undefined}
      size="sm"
      submitLabel="Mark as lost"
      danger
      pending={pending}
      onSubmit={(f) => onConfirm(f.reason)}
    >
      <Field label="Why was it lost?" className="sm:col-span-2">
        <input
          name="reason"
          required
          maxLength={500}
          autoComplete="off"
          placeholder="Went with a competitor on price"
          className={fieldControl}
        />
      </Field>
    </FormDialog>
  );
}
