import { api } from "@/lib/api";

export type ModuleKind = "campaigns" | "products" | "quotes" | "communications" | "automations";
export type QuoteLine = { id: string; productId: string; name: string; quantity: number; unitPrice: number; discountPct: number; taxPct: number };
export type FlowNode = { id: string; type: "Trigger" | "Condition" | "Action" | "Delay" | "Branch"; label: string; x?: number; y?: number };
export type FlowEdge = { id: string; source: string; target: string; label?: string };
export type ModuleRecord = {
  id: string; name: string; status: string; owner: string; account: string; description: string;
  amount: number; createdAt: string; updatedAt: string; version: number;
  lineItems?: QuoteLine[]; flowNodes?: FlowNode[]; flowEdges?: FlowEdge[];
  recipient?: string; bodyHtml?: string;
};
export type ModuleValues = Omit<ModuleRecord, "id" | "owner" | "createdAt" | "updatedAt" | "version">;

export const modulesApi = {
  list: (kind: ModuleKind) => api<ModuleRecord[]>(`/workspaces/${kind}`),
  get: (kind: ModuleKind, id: string) => api<ModuleRecord>(`/workspaces/${kind}/${id}`),
  create: (kind: ModuleKind, values: ModuleValues) => api<ModuleRecord>(`/workspaces/${kind}`, { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify(values) }),
  update: async (kind: ModuleKind, id: string, changes: Partial<ModuleRecord>) => {
    const current = await modulesApi.get(kind, id);
    return api<ModuleRecord>(`/workspaces/${kind}/${id}`, { method: "PATCH", body: JSON.stringify({ ...current, ...changes, version: current.version }) });
  },
  archive: (kind: ModuleKind, ids: string[]) => Promise.all(ids.map((id) => api(`/workspaces/${kind}/${id}`, { method: "DELETE" }))),
};
