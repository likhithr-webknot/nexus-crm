import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
});

const mono = JetBrains_Mono({
  variable: "--font-mono-jb",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: { default: "Nexus CRM", template: "%s · Nexus CRM" },
  description: "Leads, pipeline and revenue in one place.",
};

// The signed-in shell (sidebar, top bar) lives in app/(app)/layout.tsx so that routes outside it, such as /login, render without it.
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" suppressHydrationWarning className={`${inter.variable} ${mono.variable} h-full antialiased`}>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: "try{document.documentElement.dataset.theme=localStorage.getItem('nexus.theme')||'light'}catch{}",
          }}
        />
      </head>
      <body className="h-full font-sans text-[14px]">{children}</body>
    </html>
  );
}
