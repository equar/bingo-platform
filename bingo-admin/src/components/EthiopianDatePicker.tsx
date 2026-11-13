import { useState } from "react";
import type { MouseEvent } from "react";
import {
  Box,
  Button,
  InputAdornment,
  Popover,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import {
  ethiopianDateInputToGregorianDate,
  ethiopianMonthName,
  formatEthiopianNumericDate,
} from "../utils/ethiopianDateTime";

interface EthiopianDate {
  year: number;
  month: number;
  day: number;
}

interface EthiopianDatePickerProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  required?: boolean;
  error?: boolean;
  helperText?: string;
}

function parseDate(value: string): EthiopianDate | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const [, year, month, day] = match;
  return { year: Number(year), month: Number(month), day: Number(day) };
}

function dateValue({ year, month, day }: EthiopianDate): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function shiftMonth({ year, month }: EthiopianDate, amount: number): EthiopianDate {
  const totalMonths = year * 13 + month - 1 + amount;
  return {
    year: Math.floor(totalMonths / 13),
    month: ((totalMonths % 13) + 13) % 13 + 1,
    day: 1,
  };
}

function monthDays(year: number, month: number): number[] {
  return Array.from({ length: 30 }, (_, index) => index + 1)
    .filter((day) => ethiopianDateInputToGregorianDate(dateValue({ year, month, day })) !== null);
}

function dayOfWeek(year: number, month: number): number {
  const gregorianDate = ethiopianDateInputToGregorianDate(dateValue({ year, month, day: 1 }));
  return gregorianDate ? new Date(`${gregorianDate}T12:00:00Z`).getUTCDay() : 0;
}

function displayDate(value: string): string {
  const gregorianDate = ethiopianDateInputToGregorianDate(value);
  return gregorianDate ? formatEthiopianNumericDate(`${gregorianDate}T12:00:00Z`) : "";
}

export function EthiopianDatePicker({
  label,
  value,
  onChange,
  disabled,
  required,
  error,
  helperText,
}: EthiopianDatePickerProps) {
  const [anchor, setAnchor] = useState<HTMLButtonElement | null>(null);
  const selectedDate = parseDate(value);
  const [visibleMonth, setVisibleMonth] = useState<EthiopianDate | null>(selectedDate);
  const displayedMonth = visibleMonth ?? selectedDate;
  const days = displayedMonth ? monthDays(displayedMonth.year, displayedMonth.month) : [];
  const padding = displayedMonth ? dayOfWeek(displayedMonth.year, displayedMonth.month) : 0;

  function openCalendar(event: MouseEvent<HTMLButtonElement>) {
    setVisibleMonth(selectedDate);
    setAnchor(event.currentTarget);
  }

  function selectDate(day: number) {
    if (!displayedMonth) return;
    onChange(dateValue({ ...displayedMonth, day }));
    setAnchor(null);
  }

  return (
    <>
      <TextField
        label={label}
        value={displayDate(value)}
        fullWidth
        required={required}
        error={error}
        helperText={helperText}
        disabled={disabled}
        inputProps={{ readOnly: true }}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <Button
                onClick={openCalendar}
                disabled={disabled}
                aria-label={`Open ${label} calendar`}
                size="small"
              >
                Calendar
              </Button>
            </InputAdornment>
          ),
        }}
      />
      <Popover
        open={Boolean(anchor)}
        anchorEl={anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
      >
        {displayedMonth ? (
          <Box sx={{ p: 2, minWidth: 320 }}>
            <Stack direction="row" alignItems="center" justifyContent="space-between" mb={1}>
              <Button
                aria-label="Previous Ethiopian month"
                onClick={() => setVisibleMonth(shiftMonth(displayedMonth, -1))}
              >
                Previous
              </Button>
              <Typography component="h2" variant="subtitle1">
                {ethiopianMonthName(displayedMonth.month)} {displayedMonth.year}
              </Typography>
              <Button
                aria-label="Next Ethiopian month"
                onClick={() => setVisibleMonth(shiftMonth(displayedMonth, 1))}
              >
                Next
              </Button>
            </Stack>
            <Box display="grid" gridTemplateColumns="repeat(7, 1fr)" gap={0.5} textAlign="center">
              {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => (
                <Typography key={day} variant="caption" color="text.secondary">
                  {day}
                </Typography>
              ))}
              {Array.from({ length: padding }).map((_, index) => (
                <Box key={`padding-${index}`} />
              ))}
              {days.map((day) => {
                const candidate = dateValue({ ...displayedMonth, day });
                return (
                  <Button
                    key={candidate}
                    aria-label={`Select ${displayDate(candidate)}`}
                    aria-pressed={candidate === value}
                    variant={candidate === value ? "contained" : "text"}
                    onClick={() => selectDate(day)}
                    sx={{ minWidth: 0, px: 0.5 }}
                  >
                    {day}
                  </Button>
                );
              })}
            </Box>
          </Box>
        ) : null}
      </Popover>
    </>
  );
}
