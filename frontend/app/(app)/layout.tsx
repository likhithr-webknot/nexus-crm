import { SessionProvider } from "@/components/auth/session";
import { CurrencyProvider } from "@/components/currency/currency-provider";
import { AppShell } from "@/components/shell/app-shell";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <CurrencyProvider>
        <AppShell>{children}</AppShell>
      </CurrencyProvider>
    </SessionProvider>
  );
}
