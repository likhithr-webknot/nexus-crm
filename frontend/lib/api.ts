/*
 * The one place the browser talks to the backend. It sends the session
 * cookies, adds the CSRF header to requests that change data, and renews an
 * expired access token once before giving up.
 * Contract: backend/README.md, "How sign-in works".
 */

// Same-origin by default (a reverse proxy routes /api to the backend). Local
// development points this at the backend's own port via .env.local.
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1").replace(/\/+$/, "");

/** Set by this file, not the backend: the session could not be renewed. */
export const SESSION_EXPIRED = "session_expired";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    /** The backend's error code, for example "not_authenticated". */
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

/** True when the error means "nobody is signed in", as opposed to a fault. */
export const isSignedOut = (error: unknown) => error instanceof ApiError && error.status === 401;

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function send(path: string, init: RequestInit) {
  const headers = new Headers(init.headers);
  const token = csrfToken();
  if (token && !/^(GET|HEAD)$/i.test(init.method ?? "GET")) headers.set("X-XSRF-TOKEN", token);
  if (init.body) headers.set("Content-Type", "application/json");
  return fetch(API_BASE_URL + path, { ...init, headers, credentials: "include" });
}

async function toError(response: Response) {
  // Errors are RFC 9457 problem documents; a proxy in between may send something else.
  const problem = await response.json().catch(() => null);
  return new ApiError(response.status, problem?.code ?? "unknown", problem?.detail ?? response.statusText);
}

// Several requests can find the token expired at the same moment. They share
// one refresh, because the refresh token is replaced each time it is used.
let refreshing: Promise<boolean> | null = null;

function refresh() {
  refreshing ??= send("/auth/refresh", { method: "POST" })
    .then((response) => response.ok)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/** Calls the API and returns the parsed body (undefined when there is none). Throws ApiError on failure. */
export async function api<T = void>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init);

  if (response.status === 401 && (await toError(response.clone())).code === "token_expired") {
    if (!(await refresh())) throw new ApiError(401, SESSION_EXPIRED, "The session has ended. Sign in again.");
    response = await send(path, init);
  }

  if (!response.ok) throw await toError(response);
  return (response.status === 204 ? undefined : await response.json()) as T;
}
