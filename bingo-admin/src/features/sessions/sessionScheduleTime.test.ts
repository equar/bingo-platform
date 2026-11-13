import { describe, expect, it } from "vitest";
import { getDefaultEthiopianRoomScheduleTime } from "./sessionScheduleTime";

describe("getDefaultEthiopianRoomScheduleTime", () => {
  it("uses the same default instant for the date and time across Ethiopian midnight", () => {
    expect(
      getDefaultEthiopianRoomScheduleTime(
        "Africa/Addis_Ababa",
        new Date("2026-01-01T20:55:00.001Z"),
      ),
    ).toEqual({
      scheduledDate: "2018-04-24",
      scheduledTime: "00:00",
    });
  });
});
