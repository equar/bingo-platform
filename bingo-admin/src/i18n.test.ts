import { afterEach, describe, expect, it, vi } from "vitest";
import i18n, { resolvePreferredLanguage, translateAdminStatus, translateAdminValue } from "./i18n";

describe("Admin translations", () => {
  afterEach(async () => {
    window.localStorage.clear();
    vi.resetModules();
    await i18n.changeLanguage("en");
  });

  it("keeps English and Amharic resource keys in parity", () => {
    const englishKeys = Object.keys(i18n.getResourceBundle("en", "translation")).sort();
    const amharicKeys = Object.keys(i18n.getResourceBundle("am", "translation")).sort();

    expect(amharicKeys).toEqual(englishKeys);
  });

  it("preserves labels containing colons in both languages", () => {
    expect(i18n.getFixedT("en")("Started:")).toBe("Started:");
    expect(i18n.getFixedT("en")("Capacity:")).toBe("Capacity:");
    expect(i18n.getFixedT("am")("Started:")).toBe("የተጀመረው፦");
  });

  it("translates representative operational labels", () => {
    expect(i18n.getFixedT("en")("Dashboard")).toBe("Dashboard");
    expect(i18n.getFixedT("am")("Dashboard")).toBe("ዳሽቦርድ");
    expect(i18n.getFixedT("am")("Save")).toBe("አስቀምጥ");
  });

  it("translates known status values while preserving a humanized fallback for unknown values", () => {
    const amharic = i18n.getFixedT("am");
    const english = i18n.getFixedT("en");

    expect(translateAdminStatus("pending_review", amharic)).toBe("ግምገማ በመጠባበቅ ላይ");
    expect(translateAdminStatus("FAILED_SERVER_VALIDATION", amharic)).toBe("የአገልጋይ ማረጋገጫ አልተሳካም");
    expect(translateAdminStatus("SETTLED", amharic)).toBe("ክፍያ ተፈጽሟል");
    expect(translateAdminStatus("VOID", amharic)).toBe("ተሽሯል");
    expect(translateAdminValue("UPDATE_SESSION_STATE", amharic)).toBe("የክፍለ ጊዜ ሁኔታን ያዘምኑ");
    expect(translateAdminStatus("FUTURE_VENDOR_STATE", amharic)).toBe("FUTURE VENDOR STATE");
    expect(translateAdminStatus("FUTURE_VENDOR_STATE", english)).toBe("FUTURE VENDOR STATE");
  });

  it("resolves only supported language codes and defaults to English", () => {
    expect(resolvePreferredLanguage("am")).toBe("am");
    expect(resolvePreferredLanguage("en")).toBe("en");
    expect(resolvePreferredLanguage("fr")).toBe("en");
    expect(resolvePreferredLanguage(undefined)).toBe("en");
    expect(resolvePreferredLanguage(null)).toBe("en");
  });

  it("initializes with the signed-in account's cached preference to avoid a language flash", async () => {
    vi.resetModules();
    window.localStorage.setItem(
      "bingo-admin-session",
      JSON.stringify({
        accessToken: "token",
        tokenType: "Bearer",
        user: { id: 1, preferredLanguage: "am" },
      }),
    );
    const fresh = (await import("./i18n")).default;
    expect(fresh.language).toBe("am");
  });

  it("falls back to English when the cached preference is unsupported", async () => {
    vi.resetModules();
    window.localStorage.setItem(
      "bingo-admin-session",
      JSON.stringify({
        accessToken: "token",
        tokenType: "Bearer",
        user: { id: 1, preferredLanguage: "fr" },
      }),
    );
    const fresh = (await import("./i18n")).default;
    expect(fresh.language).toBe("en");
  });

  it("updates the document language when the active language changes", async () => {
    await i18n.changeLanguage("am");
    expect(document.documentElement.lang).toBe("am");
    await i18n.changeLanguage("en");
    expect(document.documentElement.lang).toBe("en");
  });
});
