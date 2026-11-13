import { ethiopianDateInputToGregorianDate, formatEthiopianDateInput } from "../../utils/ethiopianDateTime";

interface DateTimeParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

export type ScheduleTimeResult =
  | { instant: Date; error: null }
  | { instant: null; error: string };

function wallTimeParts(value: string): DateTimeParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) return null;
  const [, year, month, day, hour, minute] = match;
  const parts = {
    year: Number(year), month: Number(month), day: Number(day),
    hour: Number(hour), minute: Number(minute),
  };
  const check = new Date(Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute));
  if (check.getUTCFullYear() !== parts.year || check.getUTCMonth() + 1 !== parts.month
      || check.getUTCDate() !== parts.day || check.getUTCHours() !== parts.hour
      || check.getUTCMinutes() !== parts.minute) return null;
  return parts;
}

function partsAt(instant: Date, timeZone: string): DateTimeParts {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(instant);
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value);
  return { year: value("year"), month: value("month"), day: value("day"), hour: value("hour"), minute: value("minute") };
}

function sameParts(left: DateTimeParts, right: DateTimeParts) {
  return left.year === right.year && left.month === right.month && left.day === right.day
    && left.hour === right.hour && left.minute === right.minute;
}

function matchingInstants(parts: DateTimeParts, timeZone: string): Date[] {
  const wallTimeAsUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute);
  const offsets = new Set<number>();
  for (const hours of [-36, -12, 0, 12, 36]) {
    const sample = new Date(wallTimeAsUtc + hours * 60 * 60 * 1000);
    const local = partsAt(sample, timeZone);
    const localAsUtc = Date.UTC(local.year, local.month - 1, local.day, local.hour, local.minute);
    offsets.add(localAsUtc - sample.getTime());
  }
  return [...offsets]
    .map((offset) => new Date(wallTimeAsUtc - offset))
    .filter((candidate) => sameParts(partsAt(candidate, timeZone), parts))
    .sort((left, right) => left.getTime() - right.getTime());
}

export function formatDateTimeLocal(instant: Date, timeZone: string): string {
  try {
    const parts = partsAt(instant, timeZone);
    return `${String(parts.year).padStart(4, "0")}-${String(parts.month).padStart(2, "0")}-${String(parts.day).padStart(2, "0")}`
      + `T${String(parts.hour).padStart(2, "0")}:${String(parts.minute).padStart(2, "0")}`;
  } catch {
    return "";
  }
}

export function formatEthiopianScheduleDate(instant: Date, timeZone: string): string {
  const localValue = formatDateTimeLocal(instant, timeZone);
  const localDate = localValue.slice(0, 10);
  return localDate ? formatEthiopianDateInput(`${localDate}T12:00:00Z`) : "";
}

export function resolveEthiopianRoomScheduleTime(ethiopianDate: string, time: string, timeZone: string, now = new Date()): ScheduleTimeResult {
  const gregorianDate = ethiopianDateInputToGregorianDate(ethiopianDate);
  if (!gregorianDate) return { instant: null, error: "Enter a valid Ethiopian calendar date." };
  return resolveRoomScheduleTime(`${gregorianDate}T${time}`, timeZone, now);
}

export function resolveRoomScheduleTime(value: string, timeZone: string, now = new Date()): ScheduleTimeResult {
  try {
    new Intl.DateTimeFormat("en-GB", { timeZone }).format(now);
    const parts = wallTimeParts(value);
    if (!parts) return { instant: null, error: "Enter a valid scheduled start time." };
    const candidates = matchingInstants(parts, timeZone);
    if (candidates.length === 0) {
      return { instant: null, error: "This local time does not exist in the room time zone. Choose another time." };
    }
    if (candidates.length > 1) {
      return { instant: null, error: "This local time occurs twice in the room time zone. Choose another time." };
    }
    if (candidates[0].getTime() <= now.getTime()) {
      return { instant: null, error: "Scheduled start must be in the future." };
    }
    return { instant: candidates[0], error: null };
  } catch {
    return { instant: null, error: "The selected room has an invalid time zone." };
  }
}
