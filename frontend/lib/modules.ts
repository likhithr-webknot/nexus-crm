import { api, qs } from "@/lib/api";
import type { PageResponse } from "@/lib/page";

export type ModuleKind = "campaigns" | "products" | "quotes" | "communications" | "automations";
export type QuoteLine = {
  id: string;
  productId: string;
  name: string;
  quantity: number;
  unitPrice: number;
  discountPct: number;
  taxPct: number;
};
export type FlowNode = {
  id: string;
  type: "Trigger" | "Condition" | "Action" | "Delay" | "Branch";
  label: string;
  x?: number;
  y?: number;
};
export type FlowEdge = { id: string; source: string; target: string; label?: string };
export type ModuleRecord = {
  id: string;
  name: string;
  status: string;
  owner: string;
  account: string;
  description: string;
  amount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
  lineItems?: QuoteLine[];
  flowNodes?: FlowNode[];
  flowEdges?: FlowEdge[];
  recipient?: string;
  cc?: string[];
  bcc?: string[];
  bodyHtml?: string;
};
export type ModuleValues = Omit<ModuleRecord, "id" | "owner" | "createdAt" | "updatedAt" | "version">;

export type ModuleQuery = { q?: string; status?: string; sort?: "updated" | "name"; page?: number; size?: number };

const PAGE_SIZE = 100;

export const modulesApi = {
  /** One page, with the total number of matches. */
  list: (kind: ModuleKind, query: ModuleQuery = {}) =>
    api<PageResponse<ModuleRecord>>(`/workspaces/${kind}${qs(query)}`),
  /** Every match, fetched page by page. For pickers and exports. */
  listAll: async (kind: ModuleKind, query: Omit<ModuleQuery, "page" | "size"> = {}) => {
    const items: ModuleRecord[] = [];
    for (let page = 1; ; page++) {
      const result = await modulesApi.list(kind, { ...query, page, size: PAGE_SIZE });
      items.push(...result.items);
      if (items.length >= result.total || result.items.length === 0) return items;
    }
  },
  get: (kind: ModuleKind, id: string) => api<ModuleRecord>(`/workspaces/${kind}/${id}`),
  create: (kind: ModuleKind, values: ModuleValues) =>
    api<ModuleRecord>(`/workspaces/${kind}`, {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify(values),
    }),
  update: async (kind: ModuleKind, id: string, changes: Partial<ModuleRecord>) => {
    const current = await modulesApi.get(kind, id);
    return api<ModuleRecord>(`/workspaces/${kind}/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ ...current, ...changes, version: current.version }),
    });
  },
  archive: (kind: ModuleKind, ids: string[]) =>
    Promise.all(ids.map((id) => api(`/workspaces/${kind}/${id}`, { method: "DELETE" }))),
};
