import type { NextConfig } from "next";

const nextConfig: NextConfig = {
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
