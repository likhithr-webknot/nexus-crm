"use client";

import { usePathname } from "next/navigation";
import { useRef } from "react";

/**
 * Keeps "which record is open" in the address bar: /quotes shows the list, /quotes/123 shows the
 * list with record 123 open beside it. The address changes without a page navigation, so the list
 * stays exactly as it was, and a copied link opens the same record.
 */
export function useRecordRoute(base: string) {
  const pathname = usePathname();
  const id = pathname.startsWith(`${base}/`) ? pathname.slice(base.length + 1).split("/")[0] || null : null;
  // How many records this visit has opened: closing then steps back instead of piling up history.
  const opened = useRef(0);

  return {
    id,
    open(next: string) {
      if (next === id) return;
      opened.current += 1;
      window.history.pushState(null, "", `${base}/${next}`);
    },
    close() {
      if (opened.current > 0) {
        opened.current -= 1;
        window.history.back();
      } else {
        window.history.replaceState(null, "", base);
      }
    },
  };
}
