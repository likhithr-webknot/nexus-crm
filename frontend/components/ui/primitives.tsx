import Link from "next/link";
import { ChevronRight } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { Stage } from "@/lib/mock-data";
import { cn, initials } from "@/lib/format";

/* Shared control styles, so every button, input and select looks the same. */
export const buttonPrimary =
  "inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-brand px-3.5 text-[13px] font-semibold text-white shadow-sm transition-colors hover:bg-brand-ink disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-brand";
export const buttonSecondary =
  "inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-line bg-surface px-3 text-[13px] font-medium text-ink-2 transition-colors hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-line disabled:hover:text-ink-2";
export const control =
  "h-9 rounded-lg border border-line bg-surface text-[13px] text-ink hover:border-line-strong focus:border-brand focus:outline-none";

/** For inputs and selects inside a Field. Turns red once the user leaves it invalid. */
export const fieldControl = `${control} mt-1.5 block w-full px-3 placeholder:text-ink-3 user-invalid:border-down`;

/** Shown after creating something that has no backend yet, so nobody expects it to survive a reload. */
export const SESSION_ONLY = "It stays until you reload, because this part of the API isn't built yet.";

/** A labelled form field. Wrapping the control in the label ties the two together. */
export function Field({
  label,
  optional,
  hint,
  className,
  children,
}: {
  label: string;
  optional?: boolean;
  hint?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={cn("block text-[12.5px] font-medium text-ink-2", className)}>
      {label}
      {optional && <span className="font-normal text-ink-3"> (optional)</span>}
      {children}
      {hint && <span className="mt-1 block font-normal text-ink-3">{hint}</span>}
    </label>
  );
}

export function Page({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto max-w-[1600px] space-y-5 px-4 py-6 sm:px-6 lg:px-8">{children}</div>;
}

export function PageHeader({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: React.ReactNode;
  /** Page-level actions, right-aligned. */
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-[24px] font-semibold tracking-[-0.025em] text-ink">{title}</h1>
        <p className="mt-1 text-[14px] text-ink-2">{subtitle}</p>
      </div>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </div>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  body,
  children,
}: {
  icon: LucideIcon;
  title: string;
  body: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <span className="grid size-10 place-items-center rounded-full bg-brand-soft text-brand">
        <Icon className="size-5" aria-hidden />
      </span>
      <p className="mt-3 text-[14px] font-medium text-ink">{title}</p>
      <p className="mt-1 max-w-sm text-[13px] text-ink-3">{body}</p>
      {children && <div className="mt-4">{children}</div>}
    </div>
  );
}

const BADGE_TONES = {
  neutral: "bg-slate-100 text-slate-700 ring-slate-200",
  brand: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  violet: "bg-violet-50 text-violet-700 ring-violet-200",
  green: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  amber: "bg-amber-50 text-amber-800 ring-amber-200",
  red: "bg-rose-50 text-rose-700 ring-rose-200",
};

export function Badge({ tone = "neutral", children }: { tone?: keyof typeof BADGE_TONES; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-md px-2 py-0.5 text-[12px] font-medium whitespace-nowrap ring-1 ring-inset",
        BADGE_TONES[tone],
      )}
    >
      {children}
    </span>
  );
}

/** Thin horizontal bar for a 0–100 value. */
export function Meter({ value, className }: { value: number; className?: string }) {
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-canvas">
      <div className={cn("h-full rounded-full bg-brand", className)} style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
    </div>
  );
}

export function Card({ className, children }: { className?: string; children: React.ReactNode }) {
  return (
    <section className={cn("rounded-xl border border-line bg-surface", className)}>{children}</section>
  );
}

export function CardHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="flex items-start justify-between gap-4 px-5 pt-4 pb-3">
      <div className="min-w-0">
        <h2 className="text-[15px] font-semibold tracking-[-0.01em] text-ink">{title}</h2>
        {subtitle && <p className="mt-0.5 text-[13px] text-ink-3">{subtitle}</p>}
      </div>
      {action}
    </header>
  );
}

export function CardLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className="inline-flex shrink-0 items-center gap-0.5 rounded-md px-1.5 py-1 text-[13px] font-medium text-ink-2 hover:bg-canvas hover:text-ink"
    >
      {children}
      <ChevronRight className="size-3.5" aria-hidden />
    </Link>
  );
}

const AVATAR_TONES = [
  "bg-indigo-100 text-indigo-700",
  "bg-emerald-100 text-emerald-700",
  "bg-amber-100 text-amber-800",
  "bg-sky-100 text-sky-700",
  "bg-rose-100 text-rose-700",
  "bg-violet-100 text-violet-700",
];

/** Deterministic colour per name so the same person always looks the same. */
export function Avatar({ name, size = "sm" }: { name: string; size?: "sm" | "md" }) {
  const tone = AVATAR_TONES[[...name].reduce((a, c) => a + c.charCodeAt(0), 0) % AVATAR_TONES.length];
  return (
    <span
      aria-hidden
      className={cn(
        "inline-grid shrink-0 place-items-center rounded-full font-semibold",
        size === "sm" ? "size-7 text-[11px]" : "size-8 text-[12px]",
        tone,
      )}
    >
      {initials(name)}
    </span>
  );
}

const STAGE_STYLES: Record<Stage, string> = {
  New: "bg-slate-100 text-slate-700 ring-slate-200",
  Contacted: "bg-sky-50 text-sky-700 ring-sky-200",
  Qualified: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  Demo: "bg-violet-50 text-violet-700 ring-violet-200",
  Proposal: "bg-amber-50 text-amber-800 ring-amber-200",
  Negotiation: "bg-orange-50 text-orange-700 ring-orange-200",
  Won: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  Lost: "bg-rose-50 text-rose-700 ring-rose-200",
};

/** Solid colour per stage, for dots and bars. */
export const STAGE_DOT: Record<Stage, string> = {
  New: "bg-slate-400",
  Contacted: "bg-sky-500",
  Qualified: "bg-indigo-500",
  Demo: "bg-violet-500",
  Proposal: "bg-amber-500",
  Negotiation: "bg-orange-500",
  Won: "bg-emerald-500",
  Lost: "bg-rose-500",
};

export function StageBadge({ stage }: { stage: Stage }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-md px-2 py-0.5 text-[12px] font-medium ring-1 ring-inset",
        STAGE_STYLES[stage],
      )}
    >
      {stage}
    </span>
  );
}
