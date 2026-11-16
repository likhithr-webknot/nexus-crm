"use client";

import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { cn } from "@/lib/format";

// If the browser never leaves the page (blocked navigation, backend down and
// the user comes back), stop showing "Redirecting" so they can try again.
const PENDING_TIMEOUT_MS = 10_000;

/**
 * A real link, not a fetch: sign-in is a full-page redirect to the backend,
 * so it also works before hydration and without JavaScript.
 */
export function GoogleSignInButton({ href }: { href: string }) {
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!pending) return;
    const timer = window.setTimeout(() => setPending(false), PENDING_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [pending]);

  // Returning with the Back button restores this page from the back/forward
  // cache with its old state, which would leave the button stuck.
  useEffect(() => {
    const onPageShow = (event: PageTransitionEvent) => {
      if (event.persisted) setPending(false);
    };
    window.addEventListener("pageshow", onPageShow);
    return () => window.removeEventListener("pageshow", onPageShow);
  }, []);

  return (
    <a
      href={href}
      aria-disabled={pending || undefined}
      onClick={(event) => {
        if (pending) {
          event.preventDefault();
          return;
        }
        // Opening in a new tab or window leaves this page where it is.
        const opensElsewhere = event.metaKey || event.ctrlKey || event.shiftKey || event.altKey;
        if (!opensElsewhere) setPending(true);
      }}
      className={cn(
        "flex h-11 w-full items-center justify-center gap-3 rounded-lg border border-line-strong bg-surface px-4",
        "text-[14px] font-semibold text-ink shadow-[0_1px_2px_rgb(15_23_42/0.06)] transition-colors",
        pending ? "cursor-progress text-ink-2" : "hover:border-ink-3 hover:bg-canvas active:bg-line/60",
      )}
    >
      {pending ? (
        <LoaderCircle className="size-[18px] animate-spin text-ink-3 motion-reduce:animate-none" aria-hidden />
      ) : (
        <GoogleMark />
      )}
      <span aria-live="polite">{pending ? "Redirecting to Google…" : "Continue with Google"}</span>
    </a>
  );
}

/** Google's "G" mark, in the colours its branding guidelines require. */
function GoogleMark() {
  return (
    <svg viewBox="0 0 18 18" className="size-[18px] shrink-0" aria-hidden>
      <path
        fill="#4285F4"
        d="M17.64 9.2045c0-.6381-.0573-1.2518-.1636-1.8409H9v3.4814h4.8436c-.2086 1.125-.8427 2.0782-1.7959 2.7164v2.2581h2.9087c1.7018-1.5668 2.6836-3.874 2.6836-6.615z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.4673-.8059 5.9564-2.1805l-2.9087-2.2581c-.8059.54-1.8368.8591-3.0477.8591-2.3441 0-4.3282-1.5832-5.0359-3.7105H.9573v2.3318C2.4382 15.9832 5.4818 18 9 18z"
      />
      <path
        fill="#FBBC05"
        d="M3.9641 10.71c-.18-.54-.2823-1.1168-.2823-1.71s.1023-1.17.2823-1.71V4.9582H.9573C.3477 6.1732 0 7.5477 0 9s.3477 2.8268.9573 4.0418L3.9641 10.71z"
      />
      <path
        fill="#EA4335"
        d="M9 3.5795c1.3214 0 2.5077.4541 3.4405 1.346l2.5813-2.5814C13.4632.8918 11.4259 0 9 0 5.4818 0 2.4382 2.0168.9573 4.9582L3.9641 7.29C4.6718 5.1627 6.6559 3.5795 9 3.5795z"
      />
    </svg>
  );
}
