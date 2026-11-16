import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { errorMessage } from "@/lib/api";
import { deleteCurrency, deleteRateOverride, overrideRate, setCurrencyEnabled } from "@/lib/currencies";
import { todayLocal } from "@/lib/format";
import { AFTER } from "@/lib/query-keys";
import { useMutation } from "@tanstack/react-query";

/** What an admin can do to currencies and rates. Each action reloads the lists and reports a failure as a toast. */
export function useCurrencyAdmin(onEnabled?: () => void) {
  const refresh = useRefresh();
  const done = () => void refresh(AFTER.currency);
  const onError = (e: unknown) => toast(errorMessage(e));

  return {
    toggle: useMutation({
      mutationFn: (v: { code: string; enabled: boolean }) => setCurrencyEnabled(v.code, v.enabled),
      onSuccess: () => {
        done();
        onEnabled?.();
      },
      onError,
    }),
    remove: useMutation({
      mutationFn: (code: string) => deleteCurrency(code),
      onSuccess: (_, code) => {
        toast(`${code} deleted.`);
        done();
      },
      onError,
    }),
    setRate: useMutation({
      mutationFn: (v: { currency: string; rate: string }) => overrideRate(v.currency, todayLocal(), v.rate),
      onSuccess: (r) => {
        toast(`${r.currency} rate saved.`);
        done();
      },
      onError,
    }),
    revert: useMutation({
      mutationFn: (r: { currency: string; rateDate: string }) => deleteRateOverride(r.currency, r.rateDate),
      onSuccess: () => {
        toast("Rate removed. The next daily download fills the day in again.");
        done();
      },
      onError,
    }),
  };
}
