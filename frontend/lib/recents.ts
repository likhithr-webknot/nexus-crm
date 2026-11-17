/* Records opened from the command palette, newest first. Kept in this browser only. */

export type Recent = { href: string; title: string; type: string };

const KEY = "nexus.recent";
const MAX = 6;

export function readRecents(): Recent[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(saved) ? saved.filter((item) => item?.href && item?.title).slice(0, MAX) : [];
  } catch {
    return [];
  }
}

/** Forgets them. Called when someone signs out, so the next person on this computer does not see the last person's customers. */
export function clearRecents() {
  try {
    localStorage.removeItem(KEY);
  } catch {}
}

export function rememberRecent(item: Recent) {
  try {
    const rest = readRecents().filter((other) => other.href !== item.href);
    localStorage.setItem(KEY, JSON.stringify([item, ...rest].slice(0, MAX)));
  } catch {}
}
