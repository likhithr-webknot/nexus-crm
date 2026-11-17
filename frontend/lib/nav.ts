import type { Role } from "@/lib/auth";
import {
  Activity,
  BarChart3,
  Building2,
  CalendarDays,
  Contact,
  FileText,
  Filter,
  Handshake,
  LayoutGrid,
  ListTodo,
  Mail,
  Megaphone,
  Package,
  Settings,
  Trash2,
  UserPlus,
  Users,
  Workflow,
  type LucideIcon,
} from "lucide-react";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Section heading in the sidebar. */
  group: "Overview" | "Sales" | "Engage" | "Admin";
  /** Roles that see this item. Everyone sees it when this is left out. The server checks the role again. */
  roles?: Role[];
};

export const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Dashboard", icon: LayoutGrid, group: "Overview" },
  { href: "/reports", label: "Reports & Analytics", icon: BarChart3, group: "Overview" },
  { href: "/leads", label: "Leads", icon: UserPlus, group: "Sales" },
  { href: "/contacts", label: "Contacts", icon: Contact, group: "Sales" },
  { href: "/companies", label: "Companies", icon: Building2, group: "Sales" },
  { href: "/deals", label: "Deals", icon: Handshake, group: "Sales" },
  { href: "/pipeline", label: "Sales Pipeline", icon: Filter, group: "Sales" },
  { href: "/quotes", label: "Quotes", icon: FileText, group: "Sales" },
  { href: "/products", label: "Products", icon: Package, group: "Sales" },
  { href: "/tasks", label: "Tasks", icon: ListTodo, group: "Engage" },
  { href: "/calendar", label: "Calendar", icon: CalendarDays, group: "Engage" },
  { href: "/activities", label: "Activities", icon: Activity, group: "Engage" },
  { href: "/communications", label: "Communications", icon: Mail, group: "Engage" },
  { href: "/campaigns", label: "Campaigns", icon: Megaphone, group: "Engage" },
  { href: "/automations", label: "Automations", icon: Workflow, group: "Admin", roles: ["ADMIN"] },
  { href: "/team", label: "Team Management", icon: Users, group: "Admin" },
  { href: "/trash", label: "Recycle bin", icon: Trash2, group: "Admin", roles: ["ADMIN", "MANAGER"] },
  { href: "/settings", label: "Settings", icon: Settings, group: "Admin" },
];

/** The items this role may see. */
export const navItemsFor = (role: Role) => NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(role));

export function findNavItem(pathname: string): NavItem | undefined {
  return NAV_ITEMS.find((item) => (item.href === "/" ? pathname === "/" : pathname.startsWith(item.href)));
}
