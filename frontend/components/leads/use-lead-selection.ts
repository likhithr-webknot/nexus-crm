import type { Lead } from "@/lib/leads";
import { useState } from "react";

/** The ticked leads. They are kept whole, so they can be exported after the list moves to another page. */
export function useLeadSelection(rowsOnPage: Lead[]) {
  const [selected, setSelected] = useState<ReadonlyMap<string, Lead>>(new Map());

  const toggle = (leads: Lead[], on: boolean) =>
    setSelected((prev) => {
      const next = new Map(prev);
      leads.forEach((l) => (on ? next.set(l.id, l) : next.delete(l.id)));
      return next;
    });
  const clear = () => setSelected(new Map());

  const onPage = rowsOnPage.filter((l) => selected.has(l.id)).length;
  return {
    selected,
    toggle,
    clear,
    allOnPage: rowsOnPage.length > 0 && onPage === rowsOnPage.length,
    someOnPage: onPage > 0 && onPage < rowsOnPage.length,
  };
}
