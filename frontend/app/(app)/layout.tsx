import { SessionProvider } from "@/components/auth/session";
import { CurrencyProvider } from "@/components/currency/currency-provider";
import { AppShell } from "@/components/shell/app-shell";
import { Suspense } from "react";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <CurrencyProvider>
        <Suspense
          fallback={
            <div className="grid h-full place-items-center bg-canvas text-[13px] text-ink-2">Loading workspace…</div>
          }
        >
          <AppShell>{children}</AppShell>
        </Suspense>
      </CurrencyProvider>
    </SessionProvider>
  );
}
