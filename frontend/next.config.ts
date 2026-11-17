import type { NextConfig } from "next";

const isProduction = process.env.NODE_ENV === "production";

/** The API's origin, when it is not on the same origin as the pages (local development). */
const apiOrigin = (() => {
  try {
    return new URL(process.env.NEXT_PUBLIC_API_BASE_URL ?? "").origin;
  } catch {
    return "";
  }
})();

/*
 * Content Security Policy. It stops framing, scripts and form posts from other sites, and data going to other sites.
 * It cannot stop inline scripts: Next.js writes its own into every page, and a nonce would make every page dynamic.
 * Images may come from any https site because company logos are web addresses.
 */
const csp = [
  "default-src 'self'",
  // Development tools need eval. Production does not.
  `script-src 'self' 'unsafe-inline'${isProduction ? "" : " 'unsafe-eval'"}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  `connect-src 'self' ${apiOrigin}${isProduction ? "" : " ws://localhost:* http://localhost:*"}`.trim(),
  // The assistant plays spoken answers from a blob: URL.
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
  // For older browsers that ignore frame-ancestors.
  { key: "X-Frame-Options", value: "DENY" },
  // Page addresses hold record ids, so other sites only get the origin.
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // The microphone is for voice questions to the assistant.
  { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=(self), payment=(), usb=(), interest-cohort=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  ...(isProduction ? [{ key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" }] : []),
];

const nextConfig: NextConfig = {
  // Do not announce the framework to visitors.
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
