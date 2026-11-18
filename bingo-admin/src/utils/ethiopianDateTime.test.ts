import { beforeEach, describe, expect, it } from "vitest";
import i18n from "../i18n";
import {
  ethiopianCalendarDate,
  ethiopianDateInputToGregorianDate,
  ethiopianTimeParts,
  ethiopianTimeToLocal,
  formatEthiopianDate,
  formatEthiopianDateInput,
  formatEthiopianDateTime,
  formatEthiopianNumericDate,
  formatEthiopianTime,
  formatDualCalendarDateTime,
  resolveEffectiveCalendar,
} from "./ethiopianDateTime";

describe("Ethiopian date-time formatting", () => {
  beforeEach(async () => {
    await i18n.changeLanguage("en");
    window.sessionStorage.setItem("bingo-admin-session", JSON.stringify({ user: { preferredLanguage: "am", preferredCalendar: "AUTO" } }));
  });

  it("resolves automatic calendars and explicit overrides", () => {
    expect(resolveEffectiveCalendar("en", "AUTO")).toBe("GREGORIAN");
    expect(resolveEffectiveCalendar("am", "AUTO")).toBe("ETHIOPIAN");
    expect(resolveEffectiveCalendar("ti", "AUTO")).toBe("ETHIOPIAN");
    expect(resolveEffectiveCalendar(undefined, "AUTO")).toBe("GREGORIAN");
    expect(resolveEffectiveCalendar("fr", "AUTO")).toBe("GREGORIAN");
    expect(resolveEffectiveCalendar("am", "GREGORIAN")).toBe("GREGORIAN");
    expect(resolveEffectiveCalendar("en", "ETHIOPIAN")).toBe("ETHIOPIAN");
  });

  it("shows both calendars with the selected one first for a transaction timestamp", () => {
    const formatted = formatDualCalendarDateTime("2026-09-11T00:30:00.000Z");
    expect(formatted).toContain("Ethiopian: 1 Meskerem 2019 E.C.");
    expect(formatted).toContain("EAT, UTC+03:00");
    expect(formatted).toContain("Gregorian: 11 Sept 2026");
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

  it("round-trips an Ethiopian calendar date for scheduling", () => {
    expect(formatEthiopianDateInput("2026-09-11T12:00:00.000Z")).toBe(
      "2019-01-01",
    );
    expect(ethiopianDateInputToGregorianDate("2019-01-01")).toBe("2026-09-11");
    expect(ethiopianDateInputToGregorianDate("2015-13-06")).toBe("2023-09-11");
    expect(ethiopianDateInputToGregorianDate("2018-13-06")).toBeNull();
  });

  it("uses day-first ordering for numeric Ethiopian dates", () => {
    expect(formatEthiopianNumericDate("2026-09-11T12:00:00.000Z")).toBe(
      "01-01-2019",
    );
  });

  it("converts Ethiopian daytime and nighttime selections to room-local time", () => {
    expect(ethiopianTimeParts("03:52")).toEqual({
      hour: 9,
      minute: 52,
      period: "nighttime",
    });
    expect(ethiopianTimeToLocal(9, 52, "nighttime")).toBe("03:52");
    expect(ethiopianTimeToLocal(12, 0, "daytime")).toBe("06:00");
  });
});
