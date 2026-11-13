import i18n from "../i18n";

type EthiopianDate = {
  year: number;
  month: number;
  day: number;
};

const englishMonths = [
  "Meskerem",
  "Tikimt",
  "Hidar",
  "Tahsas",
  "Tir",
  "Yekatit",
  "Megabit",
  "Miazia",
  "Ginbot",
  "Sene",
  "Hamle",
  "Nehase",
  "Pagume",
];

const amharicMonths = [
  "መስከረም",
  "ጥቅምት",
  "ኅዳር",
  "ታኅሣሥ",
  "ጥር",
  "የካቲት",
  "መጋቢት",
  "ግንቦት",
  "ሰኔ",
  "ሐምሌ",
  "ነሐሴ",
  "ጳጉሜ",
];

function isAmharic(): boolean {
  return i18n.resolvedLanguage === "am" || i18n.language === "am";
}

function asDate(value: string | number | Date): Date | null {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isFinite(date.getTime()) ? date : null;
}

function ethiopiaWallTime(instant: Date): Date {
  return new Date(instant.getTime() + 3 * 60 * 60 * 1000);
}

export function ethiopianCalendarDate(
  value: string | number | Date,
): EthiopianDate | null {
  const instant = asDate(value);
  if (!instant) return null;

  const wall = ethiopiaWallTime(instant);
  const julianDay =
    Math.floor(
      Date.UTC(wall.getUTCFullYear(), wall.getUTCMonth(), wall.getUTCDate()) /
        86_400_000,
    ) + 2_440_588;
  const days = julianDay - 1_723_856;
  const remainder = ((days % 1_461) + 1_461) % 1_461;
  const dayOfYear = (remainder % 365) + 365 * Math.floor(remainder / 1_460);

  return {
    year:
      4 * Math.floor(days / 1_461) +
      Math.floor(remainder / 365) -
      Math.floor(remainder / 1_460),
    month: Math.floor(dayOfYear / 30) + 1,
    day: (dayOfYear % 30) + 1,
  };
}

export function formatEthiopianDate(
  value: string | number | Date,
  fallback = "—",
): string {
  const date = ethiopianCalendarDate(value);
  if (!date) return fallback;
  const amharic = isAmharic();
  const months = amharic ? amharicMonths : englishMonths;
  return `${date.day} ${months[date.month - 1]} ${date.year} ${amharic ? "ዓ.ም." : "E.C."}`;
}

export function formatEthiopianTime(
  value: string | number | Date,
  fallback = "—",
): string {
  const instant = asDate(value);
  if (!instant) return fallback;

  const wall = ethiopiaWallTime(instant);
  const hour = wall.getUTCHours();
  const twelveHour = (hour + 6) % 12 || 12;
  const period = isAmharic()
    ? hour >= 6 && hour < 18
      ? "ቀን"
      : "ሌሊት"
    : hour >= 6 && hour < 18
      ? "daytime"
      : "nighttime";
  return `${twelveHour}:${String(wall.getUTCMinutes()).padStart(2, "0")} ${period}`;
}

export function formatEthiopianDateTime(
  value: string | number | Date,
  fallback = "—",
): string {
  const instant = asDate(value);
  if (!instant) return fallback;
  return `${formatEthiopianDate(instant, fallback)} · ${formatEthiopianTime(instant, fallback)}`;
}
