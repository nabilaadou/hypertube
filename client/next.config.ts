import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  agentRules: false,
  async rewrites() {
    const authServer = process.env.AUTH_SERVER_URL ?? "http://localhost:4567";
    const backend = process.env.BACKEND_URL || "http://localhost:8080";

    return [
      { source: "/auth/providers", destination: `${authServer}/auth/providers` },
      { source: "/auth/:provider(github|google_oauth2|marvin)", destination: `${authServer}/auth/:provider` },
      { source: "/auth/:provider(github|google_oauth2|marvin)/callback", destination: `${authServer}/auth/:provider/callback` },
      { source: "/api/auth/me", destination: `${backend}/api/auth/me` },
    ];
  },
};

export default nextConfig;
