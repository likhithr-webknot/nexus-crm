"use client";

import { useSyncExternalStore } from "react";

/* Whether the desktop sidebar is collapsed. Kept in localStorage so it survives a reload. */

const KEY = "nexus.sidebar.collapsed";
const listeners = new Set<() => void>();

function read() {
  try {
    return localStorage.getItem(KEY) === "1";
  } catch {
    return false;
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function toggleSidebar() {
  try {
    localStorage.setItem(KEY, read() ? "0" : "1");
  } catch {}
  listeners.forEach((listener) => listener());
}

/** Expanded on the server and on first paint. */
export const useSidebarCollapsed = () => useSyncExternalStore(subscribe, read, () => false);
