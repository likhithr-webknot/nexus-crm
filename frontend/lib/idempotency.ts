/*
 * Idempotency keys for requests that create something.
 *
 * A key is only useful if a repeat of the SAME request carries the SAME key. A double click, or a retry after a dropped
 * connection, then makes the server return the first answer instead of creating a second record. So the key is kept
 * for as long as the request stays the same, and replaced when the request changes or has succeeded.
 */

import { localId } from "@/lib/id";

type Attempt = { fingerprint: string; key: string };

/** The latest attempt for each kind of request, such as "deals.create". Lives only in this browser tab. */
const attempts = new Map<string, Attempt>();

/**
 * The key to send for this request. The same request (same kind, same content) gets the same key every time it is asked
 * for, until {@link finished} is called. A different request gets a new key.
 */
export function keyFor(kind: string, request: unknown): string {
  const fingerprint = JSON.stringify(request);
  const known = attempts.get(kind);
  if (known?.fingerprint === fingerprint) return known.key;

  const attempt = { fingerprint, key: localId() };
  attempts.set(kind, attempt);
  return attempt.key;
}

/** Call once the request has succeeded, so making the same thing on purpose later is a new request. */
export function finished(kind: string) {
  attempts.delete(kind);
}
