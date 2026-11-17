"use client";

import { Logo } from "@/components/ui/logo";
import { buttonPrimary } from "@/components/ui/primitives";
import { ApiError, isSignedOut, SESSION_EXPIRED } from "@/lib/api";
import { fetchMe, leaveSignedOut, signOut, type SessionUser } from "@/lib/auth";
import { clearRecents } from "@/lib/recents";
import { cn, initials } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { LoaderCircle, WifiOff } from "lucide-react";
import { createContext, use, useEffect, useState } from "react";

const SessionContext = createContext<SessionUser | null>(null);

/** The signed-in user. Only usable inside the signed-in part of the app. */
export function useSession() {
  const user = use(SessionContext);
  if (!user) throw new Error("useSession must be used inside SessionProvider");
  return user;
}

/** Wraps the signed-in part of the app. */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60_000,
            // Being signed out is an answer, not a fault, so retrying it is pointless.
            retry: (failures, error) => !isSignedOut(error) && failures < 2,
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <SessionGate>{children}</SessionGate>
    </QueryClientProvider>
  );
}

function SessionGate({ children }: { children: React.ReactNode }) {
  // Asked again whenever the tab regains focus, so a session that ended elsewhere is noticed.
  const { data: user, error, refetch, isFetching } = useQuery({ queryKey: KEYS.me, queryFn: fetchMe });
  const signedOut = isSignedOut(error);
  const expired = error instanceof ApiError && error.code === SESSION_EXPIRED;

  useEffect(() => {
    if (!signedOut) return;
    clearRecents();
    window.location.replace(expired ? "/login?notice=session_expired" : "/login");
  }, [signedOut, expired]);

  if (user && !signedOut) return <SessionContext value={user}>{children}</SessionContext>;

  return (
    <div className="grid h-full place-items-center bg-canvas px-6">
      {error && !signedOut ? (
        // The backend is unreachable or failing. Stay here: the session may still be fine.
        <div className="flex max-w-sm flex-col items-center text-center" role="alert">
          <span className="grid size-11 place-items-center rounded-full bg-danger-soft text-down">
            <WifiOff className="size-5" aria-hidden />
          </span>
          <h1 className="mt-4 text-[17px] font-semibold tracking-[-0.01em] text-ink">Can&apos;t reach Nexus</h1>
          <p className="mt-1.5 text-[14px] text-ink-2">
            The server didn&apos;t respond. Check your connection, then try again.
          </p>
          <button onClick={() => refetch()} disabled={isFetching} className={cn(buttonPrimary, "mt-5")}>
            {isFetching && <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />}
            Try again
          </button>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-5" role="status">
          <Logo />
          <LoaderCircle className="size-5 animate-spin text-ink-3 motion-reduce:animate-none" aria-hidden />
          <span className="sr-only">Loading your workspace</span>
        </div>
      )}
    </div>
  );
}

/** Sign-out with the state a button needs: busy while it runs, failed if the server could not be reached. */
export function useSignOut() {
  const [state, setState] = useState<"idle" | "pending" | "failed">("idle");
  const run = async (options?: { everywhere?: boolean }) => {
    setState("pending");
    try {
      await signOut(options);
    } catch (error) {
      // Already signed out on the server: the login page is still the right place to go.
      if (isSignedOut(error)) leaveSignedOut();
      else setState("failed");
    }
  };
  return { signOut: run, pending: state === "pending", failed: state === "failed" };
}

/** The user's Google photo, or their initials when there is none or it fails to load. */
export function UserAvatar({
  user,
  className,
}: {
  user: Pick<SessionUser, "displayName" | "avatarUrl">;
  className?: string;
}) {
  const [broken, setBroken] = useState(false);
  const shape = cn("size-8 shrink-0 rounded-full", className);
  return user.avatarUrl && !broken ? (
    // A plain img: the photo is a small file on Google's servers, and next/image would need that host allow-listed.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" onError={() => setBroken(true)} className={cn(shape, "object-cover")} />
  ) : (
    <span aria-hidden className={cn(shape, "grid place-items-center bg-brand-soft text-[12px] font-semibold text-brand-ink")}>
      {initials(user.displayName)}
    </span>
  );
}

/** The user's first name, for greetings inside server-rendered pages. */
export function SessionFirstName() {
  return useSession().displayName.split(" ")[0];
}

/** On the login page: someone who is already signed in goes straight to the app. */
export function RedirectIfSignedIn() {
  useEffect(() => {
    // After a refused sign-in the message matters more than any older session.
    if (new URLSearchParams(window.location.search).has("error")) return;
    fetchMe().then(
      () => window.location.replace("/"),
      () => {},
    );
  }, []);
  return null;
}
