import { Box, Chip, Stack, Typography } from "@mui/material";
import type { TicketResponse, WinClaimResponse } from "../api/admin";
import { translateAdminValue } from "../i18n";

type Translation = (key: string) => string;
type CellPosition = [number, number];

const shapePatterns: Record<string, string[][]> = {
  FOUR_CORNERS: [["#...#", ".....", ".....", ".....", "#...#"]],
  POSTAGE_STAMP: [
    ["##...", "##...", ".....", ".....", "....."],
    ["...##", "...##", ".....", ".....", "....."],
    [".....", ".....", ".....", "##...", "##..."],
    [".....", ".....", ".....", "...##", "...##"],
  ],
  X_SHAPE: [["#...#", ".#.#.", "..#..", ".#.#.", "#...#"]],
  T_SHAPE: [
    ["#####", "..#..", "..#..", "..#..", "..#.."],
    ["..#..", "..#..", "..#..", "#####"],
    ["#....", "#....", "#####", "#....", "#...."],
    ["....#", "....#", "#####", "....#", "....#"],
  ],
  L_SHAPE: [
    ["#....", "#....", "#....", "#....", "#####"],
    ["#####", "#....", "#....", "#....", "#...."],
    ["#####", "....#", "....#", "....#", "....#"],
    ["....#", "....#", "....#", "....#", "#####"],
  ],
  OUTER_FRAME: [["#####", "#...#", "#...#", "#...#", "#####"]],
};

function isMarked(value: number, called: Set<number>) {
  return value === 0 || called.has(value);
}

function completedLines(grid: number[][], called: Set<number>): CellPosition[][] {
  if (grid.length !== 5 || grid.some((row) => row.length !== 5)) return [];
  const lines: CellPosition[][] = [];
  const candidates: CellPosition[][] = [
    ...Array.from({ length: 5 }, (_, row) =>
      Array.from({ length: 5 }, (_, column) => [row, column] as CellPosition),
    ),
    ...Array.from({ length: 5 }, (_, column) =>
      Array.from({ length: 5 }, (_, row) => [row, column] as CellPosition),
    ),
    Array.from({ length: 5 }, (_, index) => [index, index] as CellPosition),
    Array.from({ length: 5 }, (_, index) => [index, 4 - index] as CellPosition),
  ];
  for (const candidate of candidates) {
    if (candidate.every(([row, column]) => isMarked(grid[row][column], called))) {
      lines.push(candidate);
    }
  }
  return lines;
}

function matchingShape(
  grid: number[][],
  called: Set<number>,
  shapes: string[][],
): CellPosition[] {
  if (grid.length !== 5 || grid.some((row) => row.length !== 5)) return [];
  for (const shape of shapes) {
    const cells = shape.flatMap((line, row) =>
      [...line].flatMap((marker, column) =>
        marker === "#" ? [[row, column] as CellPosition] : [],
      ),
    );
    if (cells.every(([row, column]) => isMarked(grid[row][column], called))) {
      return cells;
    }
  }
  return [];
}

function winningCells(
  grid: number[][],
  called: Set<number>,
  pattern: string,
): Set<string> {
  const cellKey = ([row, column]: CellPosition) => `${row}:${column}`;
  if (pattern === "FULL_HOUSE") {
    return new Set(
      grid.flatMap((row, rowIndex) =>
        row.flatMap((value, columnIndex) =>
          isMarked(value, called) ? [`${rowIndex}:${columnIndex}`] : [],
        ),
      ),
    );
  }
  const lines = completedLines(grid, called);
  if (pattern === "LINE") return new Set((lines[0] ?? []).map(cellKey));
  if (pattern === "TWO_LINES") {
    return new Set(lines.slice(0, 2).flatMap((line) => line.map(cellKey)));
  }
  return new Set((matchingShape(grid, called, shapePatterns[pattern] ?? [])).map(cellKey));
}

interface WinningTicketCardProps {
  ticket: TicketResponse;
  claim: WinClaimResponse;
  t: Translation;
}

export function WinningTicketCard({ ticket, claim, t }: WinningTicketCardProps) {
  const called = new Set(claim.calledNumbersSnapshot);
  const highlightedCells = winningCells(ticket.grid, called, claim.claimedPattern);
  const columns = ticket.grid[0]?.length ?? 5;
  const headers = ["B", "I", "N", "G", "O"].slice(0, columns);

  return (
    <Stack spacing={1} sx={{ py: 1.5, maxWidth: 560 }}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }}>
        <Chip
          color="success"
          label={`${t("Winning pattern")}: ${translateAdminValue(claim.claimedPattern, t)}`}
        />
        <Typography variant="caption" color="text.secondary">
          {t("Winning cells")} {highlightedCells.size} · {t("Called")} {claim.calledNumbersSnapshot.length}
        </Typography>
      </Stack>
      <Box
        role="grid"
        aria-label={t("Winning ticket card")}
        sx={{
          display: "grid",
          gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
          overflow: "hidden",
          border: 1,
          borderColor: "divider",
          borderRadius: 2,
          bgcolor: "background.paper",
        }}
      >
        {headers.map((header) => (
          <Box
            key={header}
            role="columnheader"
            sx={{
              py: 0.5,
              textAlign: "center",
              fontWeight: 800,
              color: "primary.contrastText",
              bgcolor: "primary.main",
            }}
          >
            {header}
          </Box>
        ))}
        {ticket.grid.flatMap((row, rowIndex) =>
          row.map((value, columnIndex) => {
            const isFree = value === 0;
            const isCalled = isMarked(value, called);
            const isWinning = highlightedCells.has(`${rowIndex}:${columnIndex}`);
            return (
              <Box
                key={`${rowIndex}-${columnIndex}`}
                role="gridcell"
                aria-label={
                  isFree
                    ? t("Free space")
                    : `${value}${isWinning ? `, ${t("Winning cells")}` : isCalled ? `, ${t("Called")}` : `, ${t("Not called")}`}`
                }
                sx={{
                  minHeight: 48,
                  px: 0.5,
                  display: "grid",
                  placeItems: "center",
                  borderTop: 1,
                  borderLeft: columnIndex === 0 ? 0 : 1,
                  borderColor: "divider",
                  bgcolor: isWinning
                    ? "success.main"
                    : isFree
                      ? "secondary.light"
                      : isCalled
                        ? "action.selected"
                        : "background.paper",
                  color: isWinning ? "success.contrastText" : "text.primary",
                  fontWeight: isWinning || isFree ? 800 : 600,
                }}
              >
                {isFree ? t("Free space") : value}
              </Box>
            );
          }),
        )}
      </Box>
      <Typography variant="caption" color="text.secondary">
        {t("Winning cells")} · {t("Called")} · {t("Not called")}
      </Typography>
    </Stack>
  );
}
