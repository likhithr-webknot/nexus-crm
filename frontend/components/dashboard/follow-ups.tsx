"use client";

import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { setFollowUpDone, type FollowUp } from "@/lib/follow-ups";
import { cn, formatDay, formatTime, localIso, todayLocal } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";
import { Check } from "lucide-react";

export function FollowUpList({ tasks }: { tasks: FollowUp[] }) {
  const refresh = useRefresh();
  const complete = useMutation({
    mutationFn: (id: string) => setFollowUpDone(id, true),
    onSuccess: () => refresh(AFTER.followUp),
    onError: (e) => toast(errorMessage(e, "Couldn't update the follow-up.")),
  });

  if (tasks.length === 0) {
    return <p className="px-5 pb-5 text-[13px] text-ink-3">You&apos;re all caught up.</p>;
  }

  const today = todayLocal();
  return (
    <ul className="divide-y divide-line">
      {tasks.map((t) => {
        const local = localIso(t.dueAt);
        const overdue = local.slice(0, 10) < today;
        const pending = complete.isPending && complete.variables === t.id;
        return (
          <li key={t.id} className="flex items-start gap-3 px-5 py-3">
            <button
              role="checkbox"
              aria-checked={pending}
              aria-label={`Mark "${t.title}" as done`}
              disabled={complete.isPending}
              onClick={() => complete.mutate(t.id)}
              className={cn(
                "mt-0.5 grid size-[18px] shrink-0 place-items-center rounded-[5px] border transition-colors",
                pending ? "border-brand bg-brand text-white" : "border-line-strong hover:border-brand",
              )}
            >
              {pending && <Check className="size-3" strokeWidth={3} />}
            </button>
            <div className="min-w-0 flex-1">
              <p className="text-[13px] font-medium text-ink">{t.title}</p>
              {t.about && (
                <p className="mt-0.5 text-[12px] text-ink-3">
                  {t.about.name}
                  {t.about.company ? ` · ${t.about.company}` : ""}
                </p>
              )}
            </div>
            <span className={cn("tnum shrink-0 text-[12px]", overdue ? "font-medium text-down" : "text-ink-3")}>
              {overdue ? `Due ${formatDay(local)}` : formatTime(local)}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
