/** @type {import('next').NextConfig} */
const backend = (process.env.MEF_BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");

const nextConfig = {
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
};

module.exports = nextConfig;
