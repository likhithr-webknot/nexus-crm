/** One page of a list, as the API sends it. Page numbers start at 1. */
export type PageResponse<T> = { items: T[]; total: number; page: number; size: number };

export type Ref = { id: string; name: string };
