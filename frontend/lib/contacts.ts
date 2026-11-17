/* Contacts and companies (backend: ContactController). */

import type { Tag } from "@/lib/tags";
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
  tags: Tag[];
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

export type Company = { id: string; name: string; domain: string | null; industry: string | null; websiteUrl: string | null; logoUrl: string | null; owner: Ref };

export const listContacts = (params: { q?: string; ownerId?: string; tag?: string; page?: number; size?: number }) =>
  api<PageResponse<Contact>>(`/contacts${qs(params)}`);

export const createContact = (body: NewContact) => api<Contact>("/contacts", { method: "POST", body: JSON.stringify(body) });
export const getContact = (id: string) => api<Contact>(`/contacts/${id}`);

export const listCompanies = (params: { q?: string; page?: number; size?: number }) =>
  api<PageResponse<Company>>(`/companies${qs(params)}`);

export const createCompany = (body: { name: string; domain?: string; industry?: string }) =>
  api<Company>("/companies", { method: "POST", body: JSON.stringify(body) });

export type ContactChanges = Partial<Omit<NewContact, "ownerId">> & { version?: number };

export const updateContact = (id: string, body: ContactChanges) =>
  api<Contact>(`/contacts/${id}`, { method: "PATCH", body: JSON.stringify(body) });

export const deleteContact = (id: string) => api(`/contacts/${id}`, { method: "DELETE" });

export const updateCompany = (id: string, body: { name?: string; domain?: string; industry?: string; version?: number }) =>
  api<Company>(`/companies/${id}`, { method: "PATCH", body: JSON.stringify(body) });

/** A company that still has contacts or deals cannot be deleted. */
export const deleteCompany = (id: string) => api(`/companies/${id}`, { method: "DELETE" });

export const getCompany = (id: string) => api<Company>(`/companies/${id}`);
