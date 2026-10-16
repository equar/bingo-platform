export type PlatformAvailability = "comingSoon" | "released";

export interface PlatformMetadata {
  availability: PlatformAvailability;
  url: string | null;
  label: string;
}

export interface SupportMetadata {
  email: string | null;
  telegramUrl: string | null;
  whatsappUrl: string | null;
}

export interface SiteConfig {
  brandName: string;
  siteUrl: string;
  indexable: boolean;
  platforms: {
    android: PlatformMetadata;
    ios: PlatformMetadata;
  };
  support: SupportMetadata;
}

const runtimeEnv =
  ((import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env ?? process.env) as Record<
    string,
    string | undefined
  >;

function readText(value: string | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function readUrl(value: string | undefined): string | null {
  const trimmed = readText(value);
  if (!trimmed) {
    return null;
  }

  try {
    return new URL(trimmed).toString();
  } catch {
    return trimmed;
  }
}

function readAvailability(value: string | undefined): PlatformAvailability {
  const normalized = value?.trim().toLowerCase();
  return normalized === "released" ? "released" : "comingSoon";
}

export const siteConfig: SiteConfig = {
  brandName: "Sunset Bingo",
  siteUrl: readUrl(runtimeEnv.PUBLIC_SITE_URL) ?? "http://localhost:4321",
  indexable: runtimeEnv.PUBLIC_SITE_INDEXABLE === "true",
  platforms: {
    android: {
      availability: readAvailability(runtimeEnv.PUBLIC_ANDROID_AVAILABILITY),
      url: readUrl(runtimeEnv.PUBLIC_ANDROID_URL),
      label: "Android",
    },
    ios: {
      availability: readAvailability(runtimeEnv.PUBLIC_IOS_AVAILABILITY),
      url: readUrl(runtimeEnv.PUBLIC_IOS_URL),
      label: "iPhone / iOS",
    },
  },
  support: {
    email: readText(runtimeEnv.PUBLIC_SUPPORT_EMAIL),
    telegramUrl: readUrl(runtimeEnv.PUBLIC_SUPPORT_TELEGRAM_URL),
    whatsappUrl: readUrl(runtimeEnv.PUBLIC_SUPPORT_WHATSAPP_URL),
  },
};
