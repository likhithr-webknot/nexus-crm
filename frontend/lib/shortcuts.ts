"use client";

import { useSyncExternalStore } from "react";

/* Keyboard shortcuts. One list, read by the key handler, the command palette and the help dialog,
   so what is shown is always what works. */

/** "G then L" style navigation: the second key, and where it goes. */
export const GO_TO: Record<string, { href: string; label: string }> = {
  d: { href: "/", label: "Dashboard" },
  l: { href: "/leads", label: "Leads" },
  c: { href: "/contacts", label: "Contacts" },
  o: { href: "/companies", label: "Companies" },
  e: { href: "/deals", label: "Deals" },
  p: { href: "/pipeline", label: "Sales Pipeline" },
  t: { href: "/tasks", label: "Tasks" },
  a: { href: "/activities", label: "Activities" },
  r: { href: "/reports", label: "Reports & Analytics" },
  s: { href: "/settings", label: "Settings" },
};

/** "N then L" style creation: the second key, and the kind of record the quick-create dialog opens. */
export const CREATE: Record<string, { kind: string; label: string }> = {
  l: { kind: "lead", label: "Lead" },
  c: { kind: "contact", label: "Contact" },
  o: { kind: "company", label: "Company" },
  d: { kind: "deal", label: "Deal" },
  t: { kind: "task", label: "Task" },
  a: { kind: "activity", label: "Activity" },
};

/** The keys to show for a page or a record kind, for example ["G", "L"]. */
export const goToKeys = (href: string) => {
  const key = Object.keys(GO_TO).find((k) => GO_TO[k].href === href);
  return key ? ["G", key.toUpperCase()] : undefined;
};
export const createKeys = (kind: string) => {
  const key = Object.keys(CREATE).find((k) => CREATE[k].kind === kind);
  return key ? ["N", key.toUpperCase()] : undefined;
};

/** Events the shell listens for. Dispatch on window to open something from anywhere. */
export const SHELL_EVENTS = { palette: "nexus:palette", shortcuts: "nexus:shortcuts", create: "nexus:create" } as const;
export const openPalette = () => window.dispatchEvent(new Event(SHELL_EVENTS.palette));
export const openShortcuts = () => window.dispatchEvent(new Event(SHELL_EVENTS.shortcuts));
export const openCreate = (kind: string) =>
  window.dispatchEvent(new CustomEvent(SHELL_EVENTS.create, { detail: kind }));

const noop = () => () => {};
const isMac = () => /Mac|iPhone|iPad/.test(navigator.platform);

/** "⌘" on Apple devices, "Ctrl" elsewhere. "Ctrl" on the server, so the first paint never mismatches. */
export function useModKey() {
  return useSyncExternalStore(
    noop,
    () => (isMac() ? "⌘" : "Ctrl"),
    () => "Ctrl",
  );
}
