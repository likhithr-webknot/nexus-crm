"use client";

import { buttonSecondary, control } from "@/components/ui/primitives";
import { listCurrencies } from "@/lib/currencies";
import { cn } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { useState } from "react";
import { useCurrencyAdmin } from "./use-currency-admin";

/** For admins: which currencies people can use, adding and deleting them, and setting today's rate by hand. */
export function CurrencyAdminPanel() {
  const all = useQuery({ queryKey: KEYS.currencies, queryFn: listCurrencies });
  const [code, setCode] = useState("");
  const { toggle, remove, setRate } = useCurrencyAdmin(() => setCode(""));
  const currencies = all.data ?? [];

  return (
    <div className="border-t border-line pt-4">
      <p className="text-[13px] font-medium text-ink">Currencies people can use</p>
      <p className="mt-0.5 text-[12.5px] text-ink-3">
        Leads and deals can use any enabled currency. INR is the base and cannot be turned off.
      </p>
      <ul className="mt-3 flex flex-wrap gap-2">
        {currencies.map((c) => (
          <li key={c.code}>
            <button
              disabled={c.base || toggle.isPending}
              onClick={() => toggle.mutate({ code: c.code, enabled: !c.enabled })}
              aria-pressed={c.enabled}
              title={c.name}
              className={cn(
                "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12.5px] font-medium transition-colors disabled:cursor-default",
                c.enabled
                  ? "border-brand bg-brand-soft text-brand-ink"
                  : "border-line text-ink-3 hover:border-line-strong",
              )}
            >
              {c.enabled && <Check className="size-3.5" aria-hidden />}
              {c.code}
            </button>
            {!c.base && !c.enabled && (
              <button
                aria-label={`Delete ${c.code}`}
                title="Delete this currency"
                disabled={remove.isPending}
                onClick={() => remove.mutate(c.code)}
                className="ml-1 text-[12px] text-ink-3 hover:text-down"
              >
                <X className="inline size-3.5" aria-hidden />
              </button>
            )}
          </li>
        ))}
      </ul>

      <form
        className="mt-3 flex items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (code.trim()) toggle.mutate({ code: code.trim().toUpperCase(), enabled: true });
        }}
      >
        <input
          value={code}
          onChange={(e) => setCode(e.target.value)}
          maxLength={3}
          placeholder="Add, e.g. EUR"
          aria-label="Currency code to enable"
          className={cn(control, "w-36 px-3 uppercase")}
        />
        <button type="submit" className={buttonSecondary} disabled={code.trim().length !== 3 || toggle.isPending}>
          Enable
        </button>
      </form>

      <form
        className="mt-4 flex flex-wrap items-end gap-2 border-t border-line pt-4"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          setRate.mutate({ currency: String(f.get("currency")), rate: String(f.get("rate")) });
          e.currentTarget.reset();
        }}
      >
        <label className="text-[12.5px] font-medium text-ink-2">
          Set today&apos;s rate by hand
          <select name="currency" className={cn(control, "mt-1.5 block px-2.5 font-normal")}>
            {currencies
              .filter((c) => c.enabled && !c.base)
              .map((c) => (
                <option key={c.code}>{c.code}</option>
              ))}
          </select>
        </label>
        <label className="text-[12.5px] font-medium text-ink-2">
          1 unit in INR
          <input
            name="rate"
            type="number"
            min="0.00000001"
            step="any"
            required
            className={cn(control, "mt-1.5 block w-32 px-3 font-normal")}
          />
        </label>
        <button type="submit" className={buttonSecondary} disabled={setRate.isPending}>
          Save rate
        </button>
      </form>
    </div>
  );
}
