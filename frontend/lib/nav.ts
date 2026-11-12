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

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
};

export const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Dashboard", icon: LayoutGrid },
  { href: "/leads", label: "Leads", icon: UserPlus, badge: 24 },
  { href: "/contacts", label: "Contacts", icon: Contact },
  { href: "/pipeline", label: "Sales Pipeline", icon: Filter },
  { href: "/follow-ups", label: "Follow-ups", icon: CalendarCheck },
  { href: "/activities", label: "Activities", icon: Activity },
  { href: "/reports", label: "Reports & Analytics", icon: BarChart3 },
  { href: "/team", label: "Team Management", icon: Users },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function findNavItem(pathname: string): NavItem | undefined {
  return NAV_ITEMS.find((item) =>
    item.href === "/" ? pathname === "/" : pathname.startsWith(item.href),
  );
}
