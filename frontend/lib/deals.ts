/* The pipeline and deals (backend: DealController). */

import { api, qs } from "@/lib/api";
import type { Money } from "@/lib/money";
import type { PageResponse, Ref } from "@/lib/page";

export type PipelineStage = {
  code: string;
  name: string;
  position: number;
  kind: "OPEN" | "WON" | "LOST";
  winProbability: number;
};

export type Deal = {
  id: string;
  name: string;
  company: Ref;
  primaryContact: Ref | null;
  owner: Ref;
  stageCode: string;
  amount: Money;
  /** The INR value saved when the deal was won or lost. Null while it is open. */
  baseAmount: string | null;
  fxRate: string | null;
  expectedCloseDate: string | null;
  closedAt: string | null;
  lostReason: string | null;
  leadId: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type NewDeal = {
  name: string;
  companyId: string;
  primaryContactId?: string;
  ownerId?: string;
  stageCode?: string;
  amount: Money;
};

export const listStages = () => api<PipelineStage[]>("/pipeline/stages");

/** Every open deal and the ones closed in the last 90 days. */
export const pipelineBoard = (ownerId?: string) => api<Deal[]>(`/pipeline/board${qs({ ownerId })}`);

export const listDeals = (params: { contactId?: string; companyId?: string; open?: boolean; page?: number; size?: number }) =>
  api<PageResponse<Deal>>(`/deals${qs(params)}`);

export const createDeal = (body: NewDeal) => api<Deal>("/deals", { method: "POST", body: JSON.stringify(body) });

export const moveDeal = (id: string, body: { stageCode: string; version: number; lostReason?: string }) =>
  api<Deal>(`/deals/${id}/stage`, { method: "PATCH", body: JSON.stringify(body) });
