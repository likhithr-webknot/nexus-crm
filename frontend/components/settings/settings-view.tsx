"use client";

import { useId, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Coins, GitBranch, History, Megaphone, RefreshCw, ShieldCheck, Target, User, X, type LucideIcon } from "lucide-react";
import { Badge, Card, CardHeader, Page, PageHeader, STAGE_DOT, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { ROLE_LABELS } from "@/lib/auth";
import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { useCurrency } from "@/components/currency/currency-provider";
import { cn, formatDate, localIso, todayLocal } from "@/lib/format";
import { errorMessage } from "@/lib/api";
import { toast } from "@/components/ui/toast";
import { listStages } from "@/lib/deals";
import { deleteCurrency, deleteRateOverride, latestRates, listCurrencies, overrideRate, setCurrencyEnabled } from "@/lib/currencies";
import { listTargets, setTarget } from "@/lib/reports";
import { listAudit } from "@/lib/audit";
import { SOURCES, SOURCE_COLORS, SOURCE_LABELS } from "@/lib/stages";

const SECTIONS: { id: string; label: string; icon: LucideIcon; adminOnly?: boolean }[] = [
  { id: "profile", label: "Your profile", icon: User },
  { id: "roles", label: "Roles & permissions", icon: ShieldCheck },
  { id: "sources", label: "Lead sources", icon: Megaphone },
  { id: "stages", label: "Pipeline stages", icon: GitBranch },
  { id: "currency", label: "Currency & rates", icon: Coins },
  { id: "targets", label: "Revenue targets", icon: Target, adminOnly: true },
  { id: "audit", label: "Audit log", icon: History, adminOnly: true },
];

export function SettingsView({ initialSection }: { initialSection?: string }) {
  const user = useSession();
  const sections = SECTIONS.filter((s) => !s.adminOnly || user.role === "ADMIN");
  const [section, setSection] = useState(sections.some((s) => s.id === initialSection) ? initialSection! : "profile");

  return (
    <Page>
      <PageHeader title="Settings" subtitle="Configure your workspace and preferences." />

      <div className="grid items-start gap-5 lg:grid-cols-[240px_minmax(0,1fr)]">
        <nav aria-label="Settings sections" className="rounded-xl border border-line bg-surface p-1.5">
          {/* A row of pills on small screens, a list beside the content on large ones. */}
          <ul className="flex gap-1 overflow-x-auto lg:flex-col">
            {sections.map(({ id, label, icon: Icon }) => (
              <li key={id} className="shrink-0">
                <button
                  onClick={() => setSection(id)}
                  aria-current={section === id ? "page" : undefined}
                  className={cn(
                    "flex h-9 w-full items-center gap-2.5 rounded-lg px-3 text-[13px] font-medium whitespace-nowrap transition-colors",
                    section === id ? "bg-brand-soft text-brand-ink" : "text-ink-2 hover:bg-canvas hover:text-ink",
                  )}
                >
                  <Icon className="size-4" aria-hidden />
                  {label}
                </button>
              </li>
            ))}
          </ul>
        </nav>

        {section === "profile" && <YourProfile />}
        {section === "roles" && <Roles />}
        {section === "sources" && <Sources />}
        {section === "stages" && <Stages />}
        {section === "currency" && <CurrencySettings />}
        {section === "targets" && <Targets />}
        {section === "audit" && <AuditLog />}
      </div>
    </Page>
  );
}

function CurrencySettings() {
  const { reportingCurrency, setReportingCurrency, rateState, refreshRates, currencies } = useCurrency();
  const user = useSession();
  const queryClient = useQueryClient();
  const all = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies, enabled: user.role === "ADMIN" });
  const rates = useQuery({ queryKey: ["exchange-rates"], queryFn: latestRates });
  const [code, setCode] = useState("");

  const refreshCurrencies = () => {
    for (const key of ["currencies", "exchange-rates"]) void queryClient.invalidateQueries({ queryKey: [key] });
  };
  const remove = useMutation({
    mutationFn: (c: string) => deleteCurrency(c),
    onSuccess: (_, c) => {
      toast(`${c} deleted.`);
      refreshCurrencies();
    },
    onError: (e) => toast(errorMessage(e)),
  });
  const setRate = useMutation({
    mutationFn: (v: { currency: string; rate: string }) => overrideRate(v.currency, todayLocal(), v.rate),
    onSuccess: (r) => {
      toast(`${r.currency} rate saved.`);
      refreshCurrencies();
    },
    onError: (e) => toast(errorMessage(e)),
  });
  const revert = useMutation({
    mutationFn: (r: { currency: string; rateDate: string }) => deleteRateOverride(r.currency, r.rateDate),
    onSuccess: () => {
      toast("Rate removed. The next daily download fills the day in again.");
      refreshCurrencies();
    },
    onError: (e) => toast(errorMessage(e)),
  });

  const toggle = useMutation({
    mutationFn: (v: { code: string; enabled: boolean }) => setCurrencyEnabled(v.code, v.enabled),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["currencies"] });
      void queryClient.invalidateQueries({ queryKey: ["exchange-rates"] });
      setCode("");
    },
    onError: (e) => toast(errorMessage(e)),
  });

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
              {!currencies.some((c) => c.code === reportingCurrency) && <option value={reportingCurrency}>{reportingCurrency}</option>}
            </select>
          )}
        </Field>
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-canvas px-3.5 py-3 text-[12.5px] text-ink-2" role="status" aria-live="polite">
          {rateState.status === "loading" && "Loading daily exchange rates…"}
          {rateState.status === "ready" && (
            <span className={rateState.stale ? "text-amber-800" : undefined}>
              {rateState.stale
                ? `Some exchange rates are more than 7 days old (newest: ${rateState.updatedAt}). Converted totals may be out of date.`
                : `Exchange rates last updated ${rateState.updatedAt}. The server downloads new rates every day.`}
            </span>
          )}
          {rateState.status === "error" && (
            <>
              <span className="text-down">Could not load exchange rates. Converted totals are unavailable.</span>
              <button onClick={refreshRates} className={buttonSecondary}>
                <RefreshCw className="size-3.5" aria-hidden />
                Retry
              </button>
            </>
          )}
        </div>

        {rates.data && rates.data.length > 0 && (
          <table className="w-full text-left text-[13px]">
            <thead>
              <tr className="border-b border-line text-[11.5px] tracking-wide text-ink-3 uppercase">
                <th className="py-2 font-medium">Currency</th>
                <th className="py-2 text-right font-medium">1 unit in INR</th>
                <th className="py-2 text-right font-medium">Rate date</th>
                <th className="py-2 text-right font-medium">Source</th>
                {user.role === "ADMIN" && <th className="w-20 py-2" />}
              </tr>
            </thead>
            <tbody className="tnum divide-y divide-line">
              {rates.data.map((r) => (
                <tr key={r.currency}>
                  <td className="py-2 font-medium text-ink">{r.currency}</td>
                  <td className="py-2 text-right text-ink-2">{Number(r.rateToBase).toFixed(4)}</td>
                  <td className={cn("py-2 text-right", r.stale ? "text-down" : "text-ink-2")}>{r.rateDate}</td>
                  <td className="py-2 text-right text-ink-3">{r.source}</td>
                  {user.role === "ADMIN" && (
                    <td className="py-2 text-right">
                      {r.source === "MANUAL" && (
                        <button
                          onClick={() => revert.mutate(r)}
                          disabled={revert.isPending}
                          className="text-[12px] font-medium text-brand hover:underline"
                        >
                          Revert
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {user.role === "ADMIN" && (
          <div className="border-t border-line pt-4">
            <p className="text-[13px] font-medium text-ink">Currencies people can use</p>
            <p className="mt-0.5 text-[12.5px] text-ink-3">Leads and deals can use any enabled currency. INR is the base and cannot be turned off.</p>
            <ul className="mt-3 flex flex-wrap gap-2">
              {(all.data ?? []).map((c) => (
                <li key={c.code}>
                  <button
                    disabled={c.base || toggle.isPending}
                    onClick={() => toggle.mutate({ code: c.code, enabled: !c.enabled })}
                    aria-pressed={c.enabled}
                    title={c.name}
                    className={cn(
                      "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12.5px] font-medium transition-colors disabled:cursor-default",
                      c.enabled ? "border-brand bg-brand-soft text-brand-ink" : "border-line text-ink-3 hover:border-line-strong",
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
                  {(all.data ?? []).filter((c) => c.enabled && !c.base).map((c) => (
                    <option key={c.code}>{c.code}</option>
                  ))}
                </select>
              </label>
              <label className="text-[12.5px] font-medium text-ink-2">
                1 unit in INR
                <input name="rate" type="number" min="0.00000001" step="any" required className={cn(control, "mt-1.5 block w-32 px-3 font-normal")} />
              </label>
              <button type="submit" className={buttonSecondary} disabled={setRate.isPending}>
                Save rate
              </button>
            </form>
          </div>
        )}
      </div>
    </Card>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: (props: { id: string; className: string; "aria-invalid": boolean; "aria-describedby"?: string }) => React.ReactNode;
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="text-[12.5px] font-medium text-ink-2">
        {label}
      </label>
      {children({
        id,
        className: cn(control, "mt-1.5 w-full px-3", error && "border-down hover:border-down focus:border-down"),
        "aria-invalid": Boolean(error),
        "aria-describedby": error ? `${id}-error` : undefined,
      })}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-[12px] text-down">
          {error}
        </p>
      )}
    </div>
  );
}

function YourProfile() {
  const user = useSession();
  const { signOut, pending, failed } = useSignOut();
  // Signing out everywhere interrupts other devices, so it takes a second click.
  const [confirming, setConfirming] = useState(false);

  return (
    <Card>
      <CardHeader title="Your profile" subtitle="Your name, email and photo come from your Google account." />
      <div className="flex items-center gap-4 border-t border-line p-5">
        <UserAvatar user={user} className="size-14 text-[17px]" />
        <div className="min-w-0">
          <p className="truncate text-[15px] font-semibold text-ink">{user.displayName}</p>
          <p className="truncate text-[13px] text-ink-2">{user.email}</p>
          <p className="mt-1.5">
            <Badge tone="brand">{ROLE_LABELS[user.role]}</Badge>
          </p>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line p-5">
        <div>
          <p className="text-[13px] font-medium text-ink">Sign out of all devices</p>
          <p className="text-[12.5px] text-ink-3">Ends every session, including this one. Use it if a device is lost.</p>
          {failed && (
            <p role="alert" className="mt-1 text-[12.5px] text-down">
              Couldn&apos;t reach the server. Try again.
            </p>
          )}
        </div>
        {confirming ? (
          <div className="flex items-center gap-2">
            <button className={buttonSecondary} disabled={pending} onClick={() => setConfirming(false)}>
              Cancel
            </button>
            <button
              className={cn(buttonPrimary, "bg-rose-600 hover:bg-rose-700 disabled:hover:bg-rose-600")}
              disabled={pending}
              onClick={() => signOut({ everywhere: true })}
            >
              {pending ? "Signing out…" : "Yes, sign out everywhere"}
            </button>
          </div>
        ) : (
          <button className={buttonSecondary} onClick={() => setConfirming(true)}>
            Sign out everywhere
          </button>
        )}
      </div>
    </Card>
  );
}

// From the backend design, "Authorization: who sees what".
const PERMISSIONS: [capability: string, rep: string, manager: string, admin: string][] = [
  ["Read and edit leads, contacts, deals, follow-ups", "Own records and the unassigned queue", "Own team's records", "All"],
  ["Reassign leads and deals", "No", "Within own team", "All"],
  ["Import CSV, restore deleted records", "No", "Yes", "Yes"],
  ["Dashboard and reports", "Own numbers", "Team numbers", "Company numbers"],
  ["Invite users, change roles, disable users", "No", "No", "Yes"],
  ["Revenue targets, audit log", "No", "No", "Yes"],
];

function Roles() {
  return (
    <Card className="overflow-hidden">
      <CardHeader title="Roles & permissions" subtitle="Three fixed roles. A member's role is changed from Team Management." />
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left text-[13px]">
          <thead>
            <tr className="border-y border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
              <th className="py-2.5 pr-3 pl-5 font-medium">Capability</th>
              <th className="px-3 py-2.5 font-medium">Rep</th>
              <th className="px-3 py-2.5 font-medium">Manager</th>
              <th className="py-2.5 pr-5 pl-3 font-medium">Admin</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {PERMISSIONS.map(([capability, ...cells]) => (
              <tr key={capability}>
                <th scope="row" className="py-3 pr-3 pl-5 font-medium text-ink">
                  {capability}
                </th>
                {cells.map((value, i) => (
                  <td key={i} className="px-3 py-3 text-ink-2 last:pr-5">
                    {value === "No" ? (
                      <span className="inline-flex items-center gap-1.5 text-ink-3">
                        <X className="size-3.5" aria-hidden /> No
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5">
                        <Check className="size-3.5 shrink-0 text-up" aria-hidden /> {value}
                      </span>
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function Sources() {
  return (
    <Card>
      <CardHeader title="Lead sources" subtitle="Where a lead can come from. Reports show each source's share of new leads." />
      <ul className="divide-y divide-line border-t border-line">
        {SOURCES.map((source) => (
          <li key={source} className="flex items-center gap-3 px-5 py-3 text-[13px]">
            <span className="size-2.5 rounded-sm" style={{ background: SOURCE_COLORS[source] }} aria-hidden />
            <span className="font-medium text-ink">{SOURCE_LABELS[source]}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Stages() {
  const stages = useQuery({ queryKey: ["pipeline", "stages"], queryFn: listStages, staleTime: Infinity });
  return (
    <Card>
      <CardHeader title="Pipeline stages" subtitle="One pipeline with fixed stages in this version. The chance to win drives the weighted forecast." />
      {stages.isPending ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Loading…</p>
      ) : (
        <ol className="divide-y divide-line border-t border-line">
          {(stages.data ?? []).map((stage) => (
            <li key={stage.code} className="flex items-center gap-3 px-5 py-3 text-[13px]">
              <span className="tnum w-4 text-ink-3">{stage.position}</span>
              <span className={cn("size-2 rounded-full", STAGE_DOT[stage.code])} aria-hidden />
              <span className="font-medium text-ink">{stage.name}</span>
              {stage.kind !== "OPEN" && <Badge>Closed</Badge>}
              <span className="tnum ml-auto text-ink-2">{stage.winProbability}% chance to win</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** The company's revenue goal for each month of a year, in INR. */
function Targets() {
  const queryClient = useQueryClient();
  const [year, setYear] = useState(new Date().getFullYear());
  const targets = useQuery({ queryKey: ["targets", year], queryFn: () => listTargets(year) });
  const saved = new Map((targets.data ?? []).map((t) => [t.month, t.amount]));

  const save = useMutation({
    mutationFn: (v: { month: string; amount: string }) => setTarget(v.month, v.amount),
    onSuccess: (t) => {
      toast(`Target for ${t.month} saved.`);
      void queryClient.invalidateQueries({ queryKey: ["targets"] });
      void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
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

/** Who changed what, newest first. */
function AuditLog() {
  const [page, setPage] = useState(1);
  const log = useQuery({ queryKey: ["audit", page], queryFn: () => listAudit({ page, size: 25 }) });
  const pageCount = Math.max(1, Math.ceil((log.data?.total ?? 0) / 25));

  return (
    <Card className="overflow-hidden">
      <CardHeader title="Audit log" subtitle="Every change to a lead, contact, company or deal, and who made it." />
      {log.isPending ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Loading…</p>
      ) : log.isError ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-down">Couldn&apos;t load the audit log.</p>
      ) : log.data.items.length === 0 ? (
        <p className="border-t border-line px-5 py-6 text-[13px] text-ink-3">Nothing has been recorded yet.</p>
      ) : (
        <ul className="divide-y divide-line border-t border-line">
          {log.data.items.map((entry) => (
            <li key={entry.id} className="px-5 py-3 text-[13px]">
              <p className="flex flex-wrap items-baseline gap-x-2">
                <span className="font-medium text-ink">{entry.actorName ?? "System"}</span>
                <span className="text-ink-2">
                  {entry.action.toLowerCase()}d a {entry.entityType.toLowerCase()}
                </span>
                <span className="tnum ml-auto text-[12px] text-ink-3">
                  {formatDate(entry.occurredAt)}, {localIso(entry.occurredAt).slice(11)}
                </span>
              </p>
              {entry.changes && (
                <ul className="mt-1 text-[12.5px] text-ink-3">
                  {Object.entries(entry.changes).map(([field, [from, to]]) => (
                    <li key={field}>
                      {field}: {String(from ?? "—")} → {String(to ?? "—")}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
      {pageCount > 1 && (
        <div className="flex items-center justify-between border-t border-line px-5 py-3 text-[12.5px] text-ink-3">
          <span>
            Page {page} of {pageCount}
          </span>
          <span className="flex gap-2">
            <button className={buttonSecondary} disabled={page === 1} onClick={() => setPage(page - 1)}>
              Newer
            </button>
            <button className={buttonSecondary} disabled={page >= pageCount} onClick={() => setPage(page + 1)}>
              Older
            </button>
          </span>
        </div>
      )}
    </Card>
  );
}
