"use client";

import { useSession } from "@/components/auth/session";
import { Page, PageHeader } from "@/components/ui/primitives";
import { cn } from "@/lib/format";
import {
  CalendarDays,
  Coins,
  GitBranch,
  History,
  Mail,
  Megaphone,
  ShieldCheck,
  Sparkles,
  Target,
  User,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { AuditSection } from "./sections/audit-section";
import { CurrencySection } from "./sections/currency-section";
import { ProfileSection } from "./sections/profile-section";
import { RolesSection } from "./sections/roles-section";
import { SourcesSection } from "./sections/sources-section";
import { StagesSection } from "./sections/stages-section";
import { TargetsSection } from "./sections/targets-section";
import { CalendarSection } from "./sections/calendar-section";
import { MailSection } from "./sections/mail-section";
import { AiKeySection } from "./sections/ai-key-section";

type Section = { id: string; label: string; icon: LucideIcon; adminOnly?: boolean; Component: () => React.ReactNode };

/** The settings sections, in order. To add one, write its component and add a line here. */
const SECTIONS: Section[] = [
  { id: "profile", label: "Your profile", icon: User, Component: ProfileSection },
  { id: "roles", label: "Roles & permissions", icon: ShieldCheck, Component: RolesSection },
  { id: "sources", label: "Lead sources", icon: Megaphone, Component: SourcesSection },
  { id: "stages", label: "Pipeline stages", icon: GitBranch, Component: StagesSection },
  { id: "currency", label: "Currency & rates", icon: Coins, Component: CurrencySection },
  { id: "mail", label: "Mail & signature", icon: Mail, Component: MailSection },
  { id: "calendar", label: "Calendars", icon: CalendarDays, Component: CalendarSection },
  { id: "ai", label: "AI & voice keys", icon: Sparkles, adminOnly: true, Component: AiKeySection },
  { id: "targets", label: "Revenue targets", icon: Target, adminOnly: true, Component: TargetsSection },
  { id: "audit", label: "Audit log", icon: History, adminOnly: true, Component: AuditSection },
];

export function SettingsView({ initialSection }: { initialSection?: string }) {
  const user = useSession();
  const sections = SECTIONS.filter((s) => !s.adminOnly || user.role === "ADMIN");
  const [selected, setSelected] = useState(sections.some((s) => s.id === initialSection) ? initialSection! : "profile");
  const Current = (sections.find((s) => s.id === selected) ?? sections[0]).Component;

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
                  onClick={() => setSelected(id)}
                  aria-current={selected === id ? "page" : undefined}
                  className={cn(
                    "flex h-9 w-full items-center gap-2.5 rounded-lg px-3 text-[13px] font-medium whitespace-nowrap transition-colors",
                    selected === id ? "bg-brand-soft text-brand-ink" : "text-ink-2 hover:bg-canvas hover:text-ink",
                  )}
                >
                  <Icon className="size-4" aria-hidden />
                  {label}
                </button>
              </li>
            ))}
          </ul>
        </nav>

        <Current />
      </div>
    </Page>
  );
}
