"use client";

import { Card, CardHeader, buttonSecondary, control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { cn } from "@/lib/format";
import { AFTER, KEYS } from "@/lib/query-keys";
import { listTargets, setTarget } from "@/lib/reports";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";


const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** The company's revenue goal for each month of a year, in INR. */
export function TargetsSection() {
  const refresh = useRefresh();
  const [year, setYear] = useState(new Date().getFullYear());
  const targets = useQuery({ queryKey: [...KEYS.targets, year], queryFn: () => listTargets(year) });
  const saved = new Map((targets.data ?? []).map((t) => [t.month, t.amount]));

  const save = useMutation({
    mutationFn: (v: { month: string; amount: string }) => setTarget(v.month, v.amount),
    onSuccess: (t) => {
      toast(`Target for ${t.month} saved.`);
      void refresh(AFTER.target);
    },
    onError: (e) => toast(errorMessage(e)),
  });

  return (
    <Card>
      <CardHeader title="Revenue targets" subtitle="The goal for each month, in INR. Only admins see targets." />
      <div className="border-t border-line p-5">
        <div className="mb-4 flex items-center gap-2">
          <button className={buttonSecondary} onClick={() => setYear(year - 1)} aria-label="Previous year">
            ‹
          </button>
          <span className="tnum w-14 text-center text-[14px] font-semibold">{year}</span>
          <button className={buttonSecondary} onClick={() => setYear(year + 1)} aria-label="Next year">
            ›
          </button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          {MONTHS.map((name, i) => {
            const month = `${year}-${String(i + 1).padStart(2, "0")}`;
            return (
              <form
                key={`${month}-${saved.get(month) ?? ""}`}
                className="flex items-center gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  const amount = String(new FormData(e.currentTarget).get("amount") ?? "").trim();
                  if (amount) save.mutate({ month, amount });
                }}
              >
                <label className="w-10 text-[13px] font-medium text-ink-2" htmlFor={`target-${month}`}>
                  {name}
                </label>
                <input
                  id={`target-${month}`}
                  name="amount"
                  type="number"
                  min={0}
                  step="any"
                  defaultValue={saved.get(month) ? Number(saved.get(month)) : ""}
                  placeholder="Not set"
                  className={cn(control, "min-w-0 flex-1 px-3")}
                />
                <button type="submit" className={buttonSecondary} disabled={save.isPending}>
                  Save
                </button>
              </form>
            );
          })}
        </div>
      </div>
    </Card>
  );
}
