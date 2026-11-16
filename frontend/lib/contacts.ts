/* Contacts and companies (backend: ContactController). */

import { api, qs } from "@/lib/api";
import type { PageResponse, Ref } from "@/lib/page";

export type Contact = {
  id: string;
  firstName: string;
  lastName: string;
  name: string;
  jobTitle: string | null;
  email: string | null;
  phone: string | null;
  company: Ref | null;
  owner: Ref;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type NewContact = {
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  jobTitle?: string;
  companyId?: string;
  companyName?: string;
  ownerId?: string;
};

export type Company = { id: string; name: string; domain: string | null; industry: string | null; owner: Ref };

export const listContacts = (params: { q?: string; page?: number; size?: number }) =>
  api<PageResponse<Contact>>(`/contacts${qs(params)}`);

export const createContact = (body: NewContact) => api<Contact>("/contacts", { method: "POST", body: JSON.stringify(body) });

export const listCompanies = (params: { q?: string; page?: number; size?: number }) =>
  api<PageResponse<Company>>(`/companies${qs(params)}`);

export const createCompany = (body: { name: string; domain?: string; industry?: string }) =>
  api<Company>("/companies", { method: "POST", body: JSON.stringify(body) });
