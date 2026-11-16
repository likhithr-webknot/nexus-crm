"use client";

import { useSyncExternalStore } from "react";

export type Theme = "light" | "dark";
const KEY = "nexus.theme";
const listeners = new Set<() => void>();

function current(): Theme {
  try { return localStorage.getItem(KEY) === "dark" ? "dark" : "light"; }
  catch { return "light"; }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, current, () => "light" as Theme);
  const setTheme = (next: Theme) => {
    const apply = () => {
      try { localStorage.setItem(KEY, next); } catch {}
      document.documentElement.dataset.theme = next;
      listeners.forEach((listener) => listener());
    };
    // Cross-fade the page where the browser can, unless the user asked for less motion.
    const fade = "startViewTransition" in document && !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (fade) document.startViewTransition(apply);
    else apply();
  };
  return { theme, setTheme };
}
