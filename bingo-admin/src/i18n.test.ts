import { describe, expect, it } from "vitest";
import i18n, { translateAdminStatus, translateAdminValue } from "./i18n";

describe("Admin translations", () => {
  it("keeps English and Amharic resource keys in parity", () => {
    const englishKeys = Object.keys(i18n.getResourceBundle("en", "translation")).sort();
    const amharicKeys = Object.keys(i18n.getResourceBundle("am", "translation")).sort();

    expect(amharicKeys).toEqual(englishKeys);
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
});
