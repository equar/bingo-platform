import type { APIRoute } from "astro";
import { siteConfig } from "../config/site";

export const GET: APIRoute = () => {
  const body = siteConfig.indexable
    ? ["User-agent: *", "Allow: /", `Host: ${new URL(siteConfig.siteUrl).host}`].join("\n")
    : ["User-agent: *", "Disallow: /"].join("\n");

  return new Response(body, {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
};
