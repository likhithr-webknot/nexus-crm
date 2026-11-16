import {
  Activity,
  BarChart3,
  CalendarCheck,
  Contact,
  Filter,
  LayoutGrid,
  Settings,
  UserPlus,
  Users,
  type LucideIcon,
} from "lucide-react";
import { LEADS } from "@/lib/mock-data";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
  /** Section heading in the sidebar. */
  group: "Overview" | "Sales" | "Engage" | "Admin";
};

export const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Dashboard", icon: LayoutGrid, group: "Overview" },
  { href: "/reports", label: "Reports & Analytics", icon: BarChart3, group: "Overview" },
  { href: "/leads", label: "Leads", icon: UserPlus, badge: LEADS.length, group: "Sales" },
  { href: "/contacts", label: "Contacts", icon: Contact, group: "Sales" },
  { href: "/pipeline", label: "Sales Pipeline", icon: Filter, group: "Sales" },
  { href: "/follow-ups", label: "Follow-ups", icon: CalendarCheck, group: "Engage" },
  { href: "/activities", label: "Activities", icon: Activity, group: "Engage" },
  { href: "/team", label: "Team Management", icon: Users, group: "Admin" },
  { href: "/settings", label: "Settings", icon: Settings, group: "Admin" },
];

export function findNavItem(pathname: string): NavItem | undefined {
  return NAV_ITEMS.find((item) =>
    item.href === "/" ? pathname === "/" : pathname.startsWith(item.href),
  );
}
