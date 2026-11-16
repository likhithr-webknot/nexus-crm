/* Spreadsheet exports. The server builds the .xlsx so every export carries the company logo
   and the same styling (backend: ExportController). */

import { apiStream } from "@/lib/api";

export type SheetCell = string | number;

/**
 * Downloads the rows as a branded Excel file. `name` is the file name without its extension;
 * `title` is the heading printed above the table.
 */
export async function downloadSheet(name: string, title: string, header: string[], rows: SheetCell[][]) {
  const response = await apiStream("/exports/sheet", { method: "POST", body: JSON.stringify({ title, header, rows }) });
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = `${name}.xlsx`;
  link.click();
  URL.revokeObjectURL(url);
}
