import Link from "next/link";
import { ChevronRight } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { stageLabel } from "@/lib/stages";
import { cn, initials } from "@/lib/format";

/* Shared control styles, so every button, input and select looks the same. */
export const buttonPrimary =
  "inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-brand px-3.5 text-[13px] font-semibold text-white shadow-sm transition-[background-color,transform] duration-[var(--duration-hover)] hover:bg-brand-ink active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-brand disabled:active:scale-100";
export const buttonSecondary =
  "inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-line bg-surface px-3 text-[13px] font-medium text-ink-2 transition-[color,background-color,border-color,transform] duration-[var(--duration-hover)] hover:border-line-strong hover:bg-surface-muted hover:text-ink active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-line disabled:hover:bg-surface disabled:hover:text-ink-2 disabled:active:scale-100";
export const control =
  "h-9 rounded-lg border border-line bg-surface text-[13px] text-ink hover:border-line-strong focus:border-brand focus:outline-none";

/** For inputs and selects inside a Field. Turns red once the user leaves it invalid. */
export const fieldControl = `${control} mt-1.5 block w-full px-3 placeholder:text-ink-3 user-invalid:border-down`;

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
  return <div className="stagger mx-auto max-w-[1600px] space-y-5 px-4 py-6 sm:px-6 lg:px-8">{children}</div>;
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
  neutral: "tone-neutral",
  brand: "tone-brand",
  violet: "tone-violet",
  green: "tone-green",
  amber: "tone-amber",
  red: "tone-red",
};

export function Badge({ tone = "neutral", children }: { tone?: keyof typeof BADGE_TONES; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        "tone inline-flex items-center rounded-md px-2 py-0.5 text-[12px] font-medium whitespace-nowrap",
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
      <div
        className={cn("h-full rounded-full bg-brand", className)}
        style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
      />
    </div>
  );
}

export function Card({ className, children, ...props }: React.ComponentProps<"section">) {
  return (
    <section {...props} className={cn("rounded-xl border border-line bg-surface", className)}>
      {children}
    </section>
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

const AVATAR_TONES = ["tone-brand", "tone-green", "tone-amber", "tone-sky", "tone-red", "tone-violet"];

/** Deterministic colour per name so the same person always looks the same. */
export function Avatar({ name, size = "sm" }: { name: string; size?: "sm" | "md" }) {
  const tone = AVATAR_TONES[[...name].reduce((a, c) => a + c.charCodeAt(0), 0) % AVATAR_TONES.length];
  return (
    <span
      aria-hidden
      className={cn(
        "tone-fill inline-grid shrink-0 place-items-center rounded-full font-semibold",
        size === "sm" ? "size-7 text-[11px]" : "size-8 text-[12px]",
        tone,
      )}
    >
      {initials(name)}
    </span>
  );
}

const STAGE_TONES: Record<string, string> = {
  NEW: "tone-neutral",
  CONTACTED: "tone-sky",
  QUALIFIED: "tone-brand",
  DISQUALIFIED: "tone-neutral",
  CONVERTED: "tone-green",
  DEMO: "tone-violet",
  PROPOSAL: "tone-amber",
  NEGOTIATION: "tone-orange",
  WON: "tone-green",
  LOST: "tone-red",
};

/** Solid colour per stage, for dots and bars. */
export const STAGE_DOT: Record<string, string> = Object.fromEntries(
  Object.entries(STAGE_TONES).map(([stage, tone]) => [stage, `tone-solid ${tone}`]),
);

/** A coloured label for a lead status or a deal stage, from its code (for example "QUALIFIED"). */
export function StageBadge({ stage }: { stage: string }) {
  return (
    <span
      className={cn(
        "tone inline-flex items-center rounded-md px-2 py-0.5 text-[12px] font-medium",
        STAGE_TONES[stage] ?? STAGE_TONES.NEW,
      )}
    >
      {stageLabel(stage)}
    </span>
  );
}
