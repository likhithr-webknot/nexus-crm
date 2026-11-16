/*
 * Sign-in contract with the backend (docs/backend-technical-design.md,
 * "Sign-in with Google"). The browser navigates to the backend, which runs the
 * whole OIDC flow and sets the session cookies; the frontend never sees a token.
 */

// Same-origin by default (reverse proxy routes /api to the backend). Local
// development points this at the backend's own port via .env.local.
const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1").replace(/\/+$/, "");

export const GOOGLE_SIGN_IN_URL = `${API_BASE_URL}/auth/oauth2/authorization/google`;

export type SignInNotice = {
  tone: "error" | "info";
  title: string;
  body: string;
};

/*
 * When sign-in is refused the backend redirects to /login?error=<code>.
 * The frontend itself links to /login?notice=<code> after sign-out or when a
 * session can no longer be refreshed.
 *
 * Only these fixed messages are ever rendered. Text from the URL is never
 * shown, so a crafted link cannot put its own words on the sign-in page.
 */
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
