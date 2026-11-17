import type { NextConfig } from "next";

const isProduction = process.env.NODE_ENV === "production";

/** The API's origin when it is not on the same origin as the pages (local development: http://localhost:8080). */
const apiOrigin = (() => {
  try {
    return new URL(process.env.NEXT_PUBLIC_API_BASE_URL ?? "").origin;
  } catch {
    return "";
  }
})();

/*
 * Content Security Policy. What it blocks: pages framing this app (clickjacking), scripts from other sites, data sent to
 * other sites by injected code (connect-src), forms posting elsewhere, plugins, and a changed <base>.
 * What it cannot block: inline scripts. Next.js writes its own inline scripts into every page, and a nonce would make every
 * page dynamic and switch off the static pages this app relies on, so script-src keeps 'unsafe-inline'. XSS is still
 * prevented by React's escaping (nothing here uses dangerouslySetInnerHTML except one fixed theme script).
 * Images may come from any https site because company logos are web addresses people or the AI assistant provide.
 */
const csp = [
  "default-src 'self'",
  // React's development tools need eval; production does not.
  `script-src 'self' 'unsafe-inline'${isProduction ? "" : " 'unsafe-eval'"}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  `connect-src 'self' ${apiOrigin}${isProduction ? "" : " ws://localhost:* http://localhost:*"}`.trim(),
  // The assistant plays spoken answers from a blob URL.
  "media-src 'self' blob:",
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Content-Type-Options", value: "nosniff" },
  // Older browsers ignore frame-ancestors; this covers them.
  { key: "X-Frame-Options", value: "DENY" },
  // Page addresses can hold record ids; other sites only get the origin, and only over https.
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // The microphone is for voice questions to the assistant. Nothing else is needed.
  { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=(self), payment=(), usb=(), interest-cohort=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  ...(isProduction ? [{ key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" }] : []),
];

const nextConfig: NextConfig = {
  // Do not announce the framework and version to every visitor.
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
  // Development only. Bottom-left would cover the sidebar's Collapse button.
  devIndicators: { position: "bottom-right" },
  /* config options here */
  experimental: {
    agentFeedback: true,
    // Every page except /login sits behind the session gate, which shows nothing until the backend confirms who is signed in.
    instantInsights: { validationLevel: "manual-warning" },
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
