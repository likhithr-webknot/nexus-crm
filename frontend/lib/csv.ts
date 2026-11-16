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
