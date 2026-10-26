const { PHASE_DEVELOPMENT_SERVER } = require("next/constants");

const backend = (process.env.MEF_BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");

// `next dev` owns `.next`; `next build` and `next start` own `.next-build`. When both shared `.next`,
// a build while a dev server was up rewrote the dev server's chunks ("Cannot find module './948.js'").
const DEV_DIST_DIR = ".next";
const BUILD_DIST_DIR = ".next-build";

/** @type {(phase: string) => import('next').NextConfig} */
module.exports = (phase) => ({
  distDir: phase === PHASE_DEVELOPMENT_SERVER ? DEV_DIST_DIR : BUILD_DIST_DIR,
  reactStrictMode: true,
  // Next 14 rewrite proxy default is 30s; backend GetAck retries can exceed it.
  experimental: { proxyTimeout: 120000 },
  async rewrites() {
    return [
      {
        source: "/backend/:path*",
        destination: `${backend}/api/:path*`,
      },
    ];
  },
});
