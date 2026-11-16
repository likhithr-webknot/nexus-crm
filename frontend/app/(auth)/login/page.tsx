import type { Metadata } from "next";
import { Suspense } from "react";
import { CircleAlert, Info } from "lucide-react";
import { GoogleSignInButton } from "@/components/auth/google-sign-in-button";
import { RedirectIfSignedIn } from "@/components/auth/session";
import { PipelinePreview } from "@/components/auth/pipeline-preview";
import { Logo } from "@/components/ui/logo";
import { GOOGLE_SIGN_IN_URL, resolveSignInNotice, type SignInNotice } from "@/lib/auth";
import { cn } from "@/lib/format";

export const metadata: Metadata = {
  title: "Sign in",
  robots: { index: false, follow: false },
};

export default function LoginPage({ searchParams }: PageProps<"/login">) {
  return (
    <div className="grid min-h-full lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <RedirectIfSignedIn />
      <main className="flex min-h-full flex-col bg-surface px-6 py-7 sm:px-10">
        <Logo />

        <div className="flex flex-1 items-center justify-center py-12">
          <div className="w-full max-w-[360px]">
            <h1 className="text-[26px] font-semibold tracking-[-0.025em] text-ink">Sign in to Nexus</h1>
            <p className="mt-2 text-[15px] leading-relaxed text-ink-2">
              Use the Google account your invitation was sent to.
            </p>

            {/* The query string is only known at request time; everything else prerenders. */}
            <Suspense fallback={null}>
              <SignInMessage searchParams={searchParams} />
            </Suspense>

            <div className="mt-7">
              <GoogleSignInButton href={GOOGLE_SIGN_IN_URL} />
            </div>

            <p className="mt-6 border-t border-line pt-5 text-[13px] leading-relaxed text-ink-2">
              <span className="font-medium text-ink">No invitation yet?</span> Nexus is invite-only. Ask your admin
              to add you from Team Management, then come back here.
            </p>
          </div>
        </div>

        <p className="text-[12px] text-ink-3">Nexus only receives your name, email and profile photo from Google.</p>
      </main>

      <aside className="hidden flex-col justify-center gap-10 bg-nav px-14 py-12 lg:flex xl:px-20">
        <div className="max-w-[460px]">
          <h2 className="text-[30px] leading-[1.15] font-semibold tracking-[-0.03em] text-white">
            Leads, pipeline and revenue in one place.
          </h2>
          <p className="mt-3 text-[15px] leading-relaxed text-nav-ink">
            Every deal, follow-up and number your team needs, current to the minute.
          </p>
        </div>
        <PipelinePreview />
      </aside>
    </div>
  );
}

async function SignInMessage({ searchParams }: Pick<PageProps<"/login">, "searchParams">) {
  const notice = resolveSignInNotice(await searchParams);
  return notice ? <Notice notice={notice} /> : null;
}

function Notice({ notice }: { notice: SignInNotice }) {
  const isError = notice.tone === "error";
  const Icon = isError ? CircleAlert : Info;
  return (
    <div
      // Errors interrupt a screen reader; informational notices wait their turn.
      role={isError ? "alert" : "status"}
      className={cn(
        "mt-6 flex gap-3 rounded-lg border px-3.5 py-3",
        isError ? "border-red-200 bg-red-50" : "border-line bg-canvas",
      )}
    >
      <Icon className={cn("mt-0.5 size-4 shrink-0", isError ? "text-down" : "text-ink-3")} aria-hidden />
      <div className="min-w-0 text-[13px] leading-relaxed">
        <p className={cn("font-semibold", isError ? "text-red-900" : "text-ink")}>{notice.title}</p>
        <p className={cn("mt-0.5", isError ? "text-red-800" : "text-ink-2")}>{notice.body}</p>
      </div>
    </div>
  );
}
