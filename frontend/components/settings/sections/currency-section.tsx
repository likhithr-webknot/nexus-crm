"use client";

import { useSession } from "@/components/auth/session";
import { useCurrency } from "@/components/currency/currency-provider";
import { Card, CardHeader } from "@/components/ui/primitives";
import { latestRates } from "@/lib/currencies";
import { KEYS } from "@/lib/query-keys";
import { useQuery } from "@tanstack/react-query";
import { CurrencyAdminPanel } from "../currency/currency-admin-panel";
import { RateStatus } from "../currency/rate-status";
import { RatesTable } from "../currency/rates-table";
import { useCurrencyAdmin } from "../currency/use-currency-admin";
import { Field } from "../settings-field";

/** Currency settings: everyone picks their reporting currency and sees the rates; admins manage currencies and rates. */
export function CurrencySection() {
  const { reportingCurrency, setReportingCurrency, currencies } = useCurrency();
  const isAdmin = useSession().role === "ADMIN";
  const rates = useQuery({ queryKey: KEYS.rates, queryFn: latestRates });
  const { revert } = useCurrencyAdmin();

  return (
    <Card>
      <CardHeader
        title="Currency & exchange rates"
        subtitle="Set the currency used for workspace totals and reports. Deal amounts keep their original currency."
      />
      <div className="space-y-4 border-t border-line p-5">
        <Field label="Reporting currency">
          {(props) => (
            <select {...props} value={reportingCurrency} onChange={(event) => setReportingCurrency(event.target.value)}>
              {currencies.map(({ code, name }) => (
                <option key={code} value={code}>
                  {code} — {name}
                </option>
              ))}
              {!currencies.some((c) => c.code === reportingCurrency) && (
                <option value={reportingCurrency}>{reportingCurrency}</option>
              )}
            </select>
          )}
        </Field>
        <RateStatus />
        {rates.data && rates.data.length > 0 && (
          <RatesTable
            rates={rates.data}
            canRevert={isAdmin}
            reverting={revert.isPending}
            onRevert={(rate) => revert.mutate(rate)}
          />
        )}
        {isAdmin && <CurrencyAdminPanel />}
      </div>
    </Card>
  );
}
