import { beforeEach, describe, expect, it } from "vitest";
import i18n from "../i18n";
import {
  ethiopianCalendarDate,
  formatEthiopianDate,
  formatEthiopianDateTime,
  formatEthiopianTime,
} from "./ethiopianDateTime";

describe("Ethiopian date-time formatting", () => {
  beforeEach(async () => {
    await i18n.changeLanguage("en");
  });

  it("uses Ethiopia's clock and calendar for API instants", () => {
    const instant = "2026-09-11T00:30:00.000Z";

    expect(ethiopianCalendarDate(instant)).toEqual({
      year: 2019,
      month: 1,
      day: 1,
    });
    expect(formatEthiopianDate(instant)).toBe("1 Meskerem 2019 E.C.");
    expect(formatEthiopianTime(instant)).toBe("9:30 nighttime");
    expect(formatEthiopianDateTime(instant)).toBe(
      "1 Meskerem 2019 E.C. · 9:30 nighttime",
    );
  });

  it("formats dates and times in Amharic when selected", async () => {
    await i18n.changeLanguage("am");

    expect(formatEthiopianDateTime("2026-09-11T20:30:00.000Z")).toBe(
      "1 መስከረም 2019 ዓ.ም. · 5:30 ሌሊት",
    );
  });

  it("uses the supplied fallback for invalid timestamps", () => {
    expect(formatEthiopianDateTime("not-a-date", "Not available")).toBe(
      "Not available",
    );
  });
});
