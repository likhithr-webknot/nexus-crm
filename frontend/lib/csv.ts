/** Builds a CSV from the rows and hands it to the browser as a download. */
export function downloadCsv(filename: string, header: string[], rows: Array<Array<string | number>>) {
  // CRM data is typed in by outsiders. A leading quote stops a spreadsheet
  // from running a cell that starts like a formula.
  const cell = (value: string | number) => {
    const text = String(value);
    return `"${(typeof value === "string" && /^[=+\-@\t\r]/.test(text) ? `'${text}` : text).replace(/"/g, '""')}"`;
  };
  const lines = [header, ...rows].map((row) => row.map(cell).join(","));
  const url = URL.createObjectURL(new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

/** Reads CSV text into rows of cells. Handles quoted cells, doubled quotes and both line endings; skips blank rows. */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  const endRow = () => {
    row.push(cell);
    rows.push(row);
    row = [];
    cell = "";
  };
  // A file saved by a spreadsheet often starts with an invisible byte-order mark.
  const input = text.replace(/^﻿/, "");
  for (let i = 0; i < input.length; i++) {
    const c = input[i];
    if (quoted) {
      if (c !== '"') cell += c;
      else if (input[i + 1] === '"') cell += input[++i];
      else quoted = false;
    } else if (c === '"') quoted = true;
    else if (c === ",") {
      row.push(cell);
      cell = "";
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && input[i + 1] === "\n") i++;
      endRow();
    } else cell += c;
  }
  if (cell || row.length) endRow();
  return rows.filter((r) => r.some((value) => value.trim()));
}
