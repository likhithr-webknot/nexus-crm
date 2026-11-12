"use client";

import { useState } from "react";
import { Check } from "lucide-react";
import type { Task } from "@/lib/mock-data";
import { cn } from "@/lib/format";

export function FollowUpList({ tasks }: { tasks: Task[] }) {
  const [done, setDone] = useState<Set<string>>(new Set());

  const toggle = (id: string) =>
    setDone((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  if (tasks.length === 0) {
    return <p className="px-5 pb-5 text-[13px] text-ink-3">You&apos;re all caught up.</p>;
  }

  return (
    <ul className="divide-y divide-line">
      {tasks.map((t) => {
        const isDone = done.has(t.id);
        return (
          <li key={t.id} className="flex items-start gap-3 px-5 py-3">
            <button
              role="checkbox"
              aria-checked={isDone}
              aria-label={`Mark "${t.title}" as done`}
              onClick={() => toggle(t.id)}
              className={cn(
                "mt-0.5 grid size-[18px] shrink-0 place-items-center rounded-[5px] border transition-colors",
                isDone ? "border-brand bg-brand text-white" : "border-line-strong hover:border-brand",
              )}
            >
              {isDone && <Check className="size-3" strokeWidth={3} />}
            </button>
            <div className="min-w-0 flex-1">
              <p className={cn("text-[13px] font-medium", isDone ? "text-ink-3 line-through" : "text-ink")}>
                {t.title}
              </p>
              <p className="mt-0.5 text-[12px] text-ink-3">{t.who}</p>
            </div>
            <span
              className={cn(
                "shrink-0 text-[12px]",
                t.overdue && !isDone ? "font-medium text-down" : "text-ink-3",
              )}
            >
              {t.due}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
