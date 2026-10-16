import { defineConfig } from "astro/config";

const siteUrl = process.env.PUBLIC_SITE_URL?.trim() || "http://localhost:4321";

export default defineConfig({
  site: siteUrl,
  output: "static",
});
