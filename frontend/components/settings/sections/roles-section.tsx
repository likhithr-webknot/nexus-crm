"use client";

import { Card, CardHeader } from "@/components/ui/primitives";
import { Check, X } from "lucide-react";

// From the backend design, "Authorization: who sees what".
const PERMISSIONS: [capability: string, rep: string, manager: string, admin: string][] = [
  [
    "Read and edit leads, contacts, deals, follow-ups",
    "Own records and the unassigned queue",
    "Own team's records",
    "All",
  ],
  ["Reassign leads and deals", "No", "Within own team", "All"],
  ["Import CSV, restore deleted records", "No", "Yes", "Yes"],
  ["Dashboard and reports", "Own numbers", "Team numbers", "Company numbers"],
  ["Invite users, change roles, disable users", "No", "No", "Yes"],
  ["Revenue targets, audit log", "No", "No", "Yes"],
];

export function RolesSection() {
  return (
    <Card className="overflow-hidden">
      <CardHeader
        title="Roles & permissions"
        subtitle="Three fixed roles. A member's role is changed from Team Management."
      />
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left text-[13px]">
          <thead>
            <tr className="border-y border-line bg-canvas/60 text-[11.5px] tracking-wide text-ink-3 uppercase">
              <th className="py-2.5 pr-3 pl-5 font-medium">Capability</th>
              <th className="px-3 py-2.5 font-medium">Rep</th>
              <th className="px-3 py-2.5 font-medium">Manager</th>
              <th className="py-2.5 pr-5 pl-3 font-medium">Admin</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {PERMISSIONS.map(([capability, ...cells]) => (
              <tr key={capability}>
                <th scope="row" className="py-3 pr-3 pl-5 font-medium text-ink">
                  {capability}
                </th>
                {cells.map((value, i) => (
                  <td key={i} className="px-3 py-3 text-ink-2 last:pr-5">
                    {value === "No" ? (
                      <span className="inline-flex items-center gap-1.5 text-ink-3">
                        <X className="size-3.5" aria-hidden /> No
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5">
                        <Check className="size-3.5 shrink-0 text-up" aria-hidden /> {value}
                      </span>
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
