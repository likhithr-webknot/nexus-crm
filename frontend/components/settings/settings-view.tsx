"use client";

import { useId, useState } from "react";
import { Bell, Building2, Check, GitBranch, Megaphone, ShieldCheck, User, X, type LucideIcon } from "lucide-react";
import { COMPANY, LEAD_SOURCES, STAGES, STAGE_PROBABILITY } from "@/lib/mock-data";
import { Badge, Card, CardHeader, Page, PageHeader, STAGE_DOT, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { ROLE_LABELS } from "@/lib/auth";
import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { cn } from "@/lib/format";

const SECTIONS: { id: string; label: string; icon: LucideIcon }[] = [
  { id: "company", label: "Company profile", icon: Building2 },
  { id: "profile", label: "Your profile", icon: User },
  { id: "roles", label: "Roles & permissions", icon: ShieldCheck },
  { id: "sources", label: "Lead sources", icon: Megaphone },
  { id: "stages", label: "Pipeline stages", icon: GitBranch },
  { id: "notifications", label: "Notifications", icon: Bell },
];

export function SettingsView({ initialSection }: { initialSection?: string }) {
  const [section, setSection] = useState(SECTIONS.some((s) => s.id === initialSection) ? initialSection! : "company");

  return (
    <Page>
      <PageHeader title="Settings" subtitle="Configure your workspace and preferences." />

      <div className="grid items-start gap-5 lg:grid-cols-[240px_minmax(0,1fr)]">
        <nav aria-label="Settings sections" className="rounded-xl border border-line bg-surface p-1.5">
          {/* A row of pills on small screens, a list beside the content on large ones. */}
          <ul className="flex gap-1 overflow-x-auto lg:flex-col">
            {SECTIONS.map(({ id, label, icon: Icon }) => (
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

        {section === "company" && <CompanyProfile />}
        {section === "profile" && <YourProfile />}
        {section === "roles" && <Roles />}
        {section === "sources" && <Sources />}
        {section === "stages" && <Stages />}
        {section === "notifications" && <Notifications />}
      </div>
    </Page>
  );
}

const INDUSTRIES = ["Software & Technology", "Financial Services", "Healthcare", "Manufacturing", "Retail", "Other"];

function CompanyProfile() {
  const [saved, setSaved] = useState(COMPANY);
  const [form, setForm] = useState(COMPANY);
  const [submitted, setSubmitted] = useState(false);
  const [justSaved, setJustSaved] = useState(false);

  const errors = {
    name: form.name.trim() ? "" : "Enter the company name.",
    email: /^\S+@\S+\.\S+$/.test(form.email) ? "" : "Enter a valid email address.",
    phone: form.phone.replace(/\D/g, "").length >= 7 ? "" : "Enter a phone number with at least 7 digits.",
  };
  const dirty = (Object.keys(form) as Array<keyof typeof form>).some((k) => form[k] !== saved[k]);
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm({ ...form, [key]: e.target.value });
    setJustSaved(false);
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (Object.values(errors).some(Boolean)) return;
    setSaved(form);
    setSubmitted(false);
    setJustSaved(true);
  };

  return (
    <Card>
      <CardHeader title="Company profile" subtitle="Your organisation's name and contact details." />
      <form onSubmit={submit} noValidate className="border-t border-line p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Company name" error={submitted ? errors.name : ""}>
            {(props) => <input {...props} value={form.name} onChange={set("name")} autoComplete="organization" />}
          </Field>
          <Field label="Industry">
            {(props) => (
              <select {...props} value={form.industry} onChange={set("industry")}>
                {INDUSTRIES.map((i) => (
                  <option key={i}>{i}</option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Company email" error={submitted ? errors.email : ""}>
            {(props) => <input {...props} type="email" value={form.email} onChange={set("email")} autoComplete="email" />}
          </Field>
          <Field label="Phone number" error={submitted ? errors.phone : ""}>
            {(props) => <input {...props} type="tel" value={form.phone} onChange={set("phone")} autoComplete="tel" />}
          </Field>
          <div className="sm:col-span-2">
            <Field label="Business address">
              {(props) => <input {...props} value={form.address} onChange={set("address")} autoComplete="street-address" />}
            </Field>
          </div>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-line pt-4">
          <button type="submit" disabled={!dirty} className={buttonPrimary}>
            Save changes
          </button>
          <button type="button" disabled={!dirty} onClick={() => setForm(saved)} className={buttonSecondary}>
            Discard
          </button>
          <p className="text-[12.5px] text-ink-3" role="status">
            {justSaved && "Saved for this session. It will reset on reload until the settings API exists."}
          </p>
        </div>
      </form>
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
      <CardHeader title="Lead sources" subtitle="Where leads come from, and each source's share this month." />
      <ul className="divide-y divide-line border-t border-line">
        {LEAD_SOURCES.map((s) => (
          <li key={s.label} className="flex items-center gap-3 px-5 py-3 text-[13px]">
            <span className="size-2.5 rounded-sm" style={{ background: s.color }} aria-hidden />
            <span className="font-medium text-ink">{s.label}</span>
            <span className="tnum ml-auto text-ink-2">{s.share}% of leads</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Stages() {
  return (
    <Card>
      <CardHeader title="Pipeline stages" subtitle="One pipeline with fixed stages in this version. The chance to win drives the weighted forecast." />
      <ol className="divide-y divide-line border-t border-line">
        {STAGES.map((stage, i) => (
          <li key={stage} className="flex items-center gap-3 px-5 py-3 text-[13px]">
            <span className="tnum w-4 text-ink-3">{i + 1}</span>
            <span className={cn("size-2 rounded-full", STAGE_DOT[stage])} aria-hidden />
            <span className="font-medium text-ink">{stage}</span>
            {(stage === "Won" || stage === "Lost") && <Badge>Closed</Badge>}
            <span className="tnum ml-auto text-ink-2">{STAGE_PROBABILITY[stage]}% chance to win</span>
          </li>
        ))}
      </ol>
    </Card>
  );
}

const NOTIFICATION_TYPES = [
  { id: "assigned", label: "A lead or deal is assigned to me", note: "So nothing waits in your queue unnoticed." },
  { id: "due", label: "A follow-up is due or overdue", note: "A reminder at the due time, and again the next morning." },
  { id: "won", label: "A deal is won", note: "For deals owned by you or your team." },
];

function Notifications() {
  const [on, setOn] = useState<ReadonlySet<string>>(new Set(NOTIFICATION_TYPES.map((n) => n.id)));
  const toggle = (id: string) =>
    setOn((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  return (
    <Card>
      <CardHeader title="Notifications" subtitle="In-app only, in the bell menu. Email notifications are not part of this version." />
      <ul className="divide-y divide-line border-t border-line">
        {NOTIFICATION_TYPES.map((n) => (
          <li key={n.id} className="flex items-center gap-4 px-5 py-3.5">
            <div className="min-w-0 flex-1">
              <p id={`notify-${n.id}`} className="text-[13px] font-medium text-ink">
                {n.label}
              </p>
              <p className="text-[12.5px] text-ink-3">{n.note}</p>
            </div>
            <button
              role="switch"
              aria-checked={on.has(n.id)}
              aria-labelledby={`notify-${n.id}`}
              onClick={() => toggle(n.id)}
              className={cn("relative h-6 w-10 shrink-0 rounded-full transition-colors", on.has(n.id) ? "bg-brand" : "bg-slate-300")}
            >
              <span
                className={cn(
                  "absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform",
                  on.has(n.id) && "translate-x-4",
                )}
              />
            </button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
