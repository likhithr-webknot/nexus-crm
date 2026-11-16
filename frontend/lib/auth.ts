/* Sign-in contract with the backend (docs/backend-technical-design.md, "Sign-in with Google"). The browser navigates to the backend, which runs the whole OIDC flow and sets the session cookies; the frontend never sees a token. */

import { API_BASE_URL, api } from "@/lib/api";

export const GOOGLE_SIGN_IN_URL = `${API_BASE_URL}/auth/oauth2/authorization/google`;

export type Role = "ADMIN" | "MANAGER" | "REP";

/** The signed-in user, as GET /me returns it. */
export type SessionUser = {
  id: string;
  email: string;
  displayName: string;
  /** The person's designation, for example "Software Engineer". */
  jobTitle: string | null;
  avatarUrl: string | null;
  role: Role;
};

export const ROLE_LABELS: Record<Role, string> = { ADMIN: "Admin", MANAGER: "Manager", REP: "Sales rep" };

export const fetchMe = () => api<SessionUser>("/me");

/** Changes the signed-in user's own name and designation. A blank designation clears it. */
export const updateProfile = (body: { displayName: string; jobTitle: string }) =>
  api<SessionUser>("/me", { method: "PATCH", body: JSON.stringify(body) });

/** A full page load, so nothing from the session stays in memory, and Back does not return to the app. */
export const leaveSignedOut = () => window.location.replace("/login?notice=signed_out");

/** Ends the session on the server, then leaves for the login page. `everywhere` also signs out every other browser and device. */
export async function signOut({ everywhere = false } = {}) {
  await api(everywhere ? "/auth/logout-all" : "/auth/logout", { method: "POST" });
  leaveSignedOut();
}

export type SignInNotice = {
  tone: "error" | "info";
  title: string;
  body: string;
};

/* When sign-in is refused the backend redirects to /login?error=<code>. Text from the URL is never shown, so a crafted link cannot put its own words on the sign-in page. */
const ERRORS: Record<string, SignInNotice> = {
  not_invited: {
    tone: "error",
    title: "This Google account doesn't have access",
    body: "Nexus is invite-only. Ask your admin to invite this email address, or sign in with a different Google account.",
  },
  account_disabled: {
    tone: "error",
    title: "This account has been disabled",
    body: "Ask your admin to restore your access.",
  },
  email_unverified: {
    tone: "error",
    title: "Your Google email isn't verified",
    body: "Verify the email address with Google, then sign in again.",
  },
  // Standard OAuth 2.0 code: the user closed or declined Google's consent screen.
  access_denied: {
    tone: "info",
    title: "Sign-in was cancelled",
    body: "No problem. Continue with Google when you're ready.",
  },
  rate_limited: {
    tone: "error",
    title: "Too many sign-in attempts",
    body: "Wait a minute, then try again.",
  },
};

const UNKNOWN_ERROR: SignInNotice = {
  tone: "error",
  title: "We couldn't sign you in",
  body: "Something went wrong on our side. Try again, and if it keeps happening, contact your admin.",
};

const NOTICES: Record<string, SignInNotice> = {
  session_expired: {
    tone: "info",
    title: "Your session has expired",
    body: "Sign in again to pick up where you left off.",
  },
  signed_out: {
    tone: "info",
    title: "You've signed out",
    body: "Sign in again whenever you're ready.",
  },
};

type QueryValue = string | string[] | undefined;

const first = (value: QueryValue) => (Array.isArray(value) ? value[0] : value);

/** Maps the /login query string to the message to show, if any. */
export function resolveSignInNotice(query: { error?: QueryValue; notice?: QueryValue }): SignInNotice | null {
  const error = first(query.error);
  if (error) return Object.hasOwn(ERRORS, error) ? ERRORS[error] : UNKNOWN_ERROR;

  const notice = first(query.notice);
  if (notice && Object.hasOwn(NOTICES, notice)) return NOTICES[notice];

  return null;
}
