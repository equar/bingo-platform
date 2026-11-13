const campaignTimeZone = "Africa/Addis_Ababa";
const dateFormatter = new Intl.DateTimeFormat("en", {
  timeZone: campaignTimeZone,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

function dateParts(date: Date) {
  const values = Object.fromEntries(
    dateFormatter.formatToParts(date).map(({ type, value }) => [type, value]),
  );
  return {
    year: Number(values.year),
    month: Number(values.month),
    day: Number(values.day),
    hour: Number(values.hour),
    minute: Number(values.minute),
    second: Number(values.second),
    millisecond: date.getUTCMilliseconds(),
  };
}

function dateValue({ year, month, day }: { year: number; month: number; day: number }) {
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function instantAtAddisMidnight(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const [, yearText, monthText, dayText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const targetWallTime = Date.UTC(year, month - 1, day);
  if (new Date(targetWallTime).toISOString().slice(0, 10) !== value) return null;

  let instant = targetWallTime;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const local = dateParts(new Date(instant));
    const representedWallTime = Date.UTC(local.year, local.month - 1, local.day,
      local.hour, local.minute, local.second, local.millisecond);
    const adjustment = targetWallTime - representedWallTime;
    instant += adjustment;
    if (adjustment === 0) break;
  }
  return new Date(instant);
}

export function getCampaignDateValue(isoString: string | null | undefined, inclusiveEnd = false) {
  if (!isoString) return "";
  const instant = new Date(isoString);
  if (Number.isNaN(instant.getTime())) return "";
  const parts = dateParts(instant);
  const date = new Date(Date.UTC(parts.year, parts.month - 1, parts.day));
  if (inclusiveEnd && instantAtAddisMidnight(dateValue(parts))?.getTime() === instant.getTime()) {
    date.setUTCDate(date.getUTCDate() - 1);
  }
  return date.toISOString().slice(0, 10);
}

export function campaignStartIso(date: string) {
  return instantAtAddisMidnight(date)?.toISOString() ?? "";
}

export function campaignEndExclusiveIso(inclusiveEndDate: string) {
  if (!instantAtAddisMidnight(inclusiveEndDate)) return "";
  const [year, month, day] = inclusiveEndDate.split("-").map(Number);
  const nextDay = new Date(Date.UTC(year, month - 1, day + 1));
  return campaignStartIso(nextDay.toISOString().slice(0, 10));
}

export function todayCampaignDateValue(now = new Date()) {
  return dateValue(dateParts(now));
}

export function isValidCampaignDateRange(effectiveFrom: string, effectiveUntil: string, now = new Date()) {
  return Boolean(
    effectiveFrom &&
      effectiveFrom >= todayCampaignDateValue(now) &&
      (!effectiveUntil || effectiveUntil >= effectiveFrom),
  );
}
