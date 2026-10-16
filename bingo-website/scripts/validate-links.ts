import { siteConfig } from "../src/config/site";

const problems: string[] = [];

function addProblem(message: string) {
  problems.push(message);
}

function isValidUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" || url.protocol === "mailto:";
  } catch {
    return false;
  }
}

function looksLikePlaceholder(value: string): boolean {
  return /example\.com|placeholder|todo|temp|your-domain|your-email|555/i.test(value);
}

if (!isValidUrl(siteConfig.siteUrl)) {
  addProblem(`PUBLIC_SITE_URL is not a valid URL: ${siteConfig.siteUrl}`);
}

if (siteConfig.platforms.android.url && !isValidUrl(siteConfig.platforms.android.url)) {
  addProblem(`PUBLIC_ANDROID_URL is not a valid URL: ${siteConfig.platforms.android.url}`);
}

if (siteConfig.platforms.android.url && looksLikePlaceholder(siteConfig.platforms.android.url)) {
  addProblem(`PUBLIC_ANDROID_URL looks like a placeholder: ${siteConfig.platforms.android.url}`);
}

if (siteConfig.platforms.ios.url && !isValidUrl(siteConfig.platforms.ios.url)) {
  addProblem(`PUBLIC_IOS_URL is not a valid URL: ${siteConfig.platforms.ios.url}`);
}

if (siteConfig.platforms.ios.url && looksLikePlaceholder(siteConfig.platforms.ios.url)) {
  addProblem(`PUBLIC_IOS_URL looks like a placeholder: ${siteConfig.platforms.ios.url}`);
}

if (siteConfig.support.email) {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(siteConfig.support.email)) {
    addProblem(`PUBLIC_SUPPORT_EMAIL is not a valid email address: ${siteConfig.support.email}`);
  }

  if (looksLikePlaceholder(siteConfig.support.email)) {
    addProblem(`PUBLIC_SUPPORT_EMAIL looks like a placeholder: ${siteConfig.support.email}`);
  }
}

for (const [label, value] of [
  ["PUBLIC_SUPPORT_TELEGRAM_URL", siteConfig.support.telegramUrl],
  ["PUBLIC_SUPPORT_WHATSAPP_URL", siteConfig.support.whatsappUrl],
] as const) {
  if (value && !isValidUrl(value)) {
    addProblem(`${label} is not a valid URL: ${value}`);
  }

  if (value && looksLikePlaceholder(value)) {
    addProblem(`${label} looks like a placeholder: ${value}`);
  }
}

if (problems.length > 0) {
  console.error("Public link validation failed:");
  for (const problem of problems) {
    console.error(`- ${problem}`);
  }
  process.exitCode = 1;
} else {
  console.log("Public links are either valid or intentionally unavailable.");
}
