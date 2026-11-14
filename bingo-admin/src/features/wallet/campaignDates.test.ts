import { describe, expect, it } from "vitest";
import {
  campaignEndExclusiveIso,
  campaignStartIso,
  getCampaignDateValue,
  isValidCampaignDateRange,
  todayCampaignDateValue,
} from "./campaignDates";

describe("campaign dates", () => {
  const now = new Date("2026-11-12T22:00:00.000Z");

  it("uses Addis Ababa dates regardless of the browser's local timezone", () => {
    expect(todayCampaignDateValue(now)).toBe("2026-11-13");
    expect(getCampaignDateValue("2026-11-12T21:00:00.000Z")).toBe("2026-11-13");
  });

  it("converts date-only start and inclusive end dates to Addis midnight instants", () => {
    expect(campaignStartIso("2026-11-13")).toBe("2026-11-12T21:00:00.000Z");
    expect(campaignEndExclusiveIso("2026-11-13")).toBe("2026-11-13T21:00:00.000Z");
    expect(getCampaignDateValue("2026-11-13T21:00:00.000Z", true)).toBe("2026-11-13");
  });

  it("allows today and future starts but rejects business-date backdating and invalid dates", () => {
    expect(isValidCampaignDateRange("2026-11-13", "2026-11-13", now)).toBe(true);
    expect(isValidCampaignDateRange("2026-11-12", "", now)).toBe(false);
    expect(campaignStartIso("2026-02-30")).toBe("");
  });
});
