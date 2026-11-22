import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { FormEvent } from "react";
import {
  Alert,
  Box,
  Button,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useLocation, useNavigate } from "react-router-dom";
import { createSession, listRooms } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { EthiopianDatePicker } from "../../components/EthiopianDatePicker";
import {
  ethiopianTimeParts,
  ethiopianTimeToLocal,
  ethiopianDateInputToGregorianDate,
  formatEthiopianDateInput,
  currentCalendarPreference,
  type EthiopianTimePeriod,
} from "../../utils/ethiopianDateTime";
import { translateAdminStatus, translateAdminValue } from "../../i18n";
import { getDefaultEthiopianRoomScheduleTime, resolveEthiopianRoomScheduleTime } from "./sessionScheduleTime";

const prizePatterns = [
  "LINE", "TWO_LINES", "FOUR_CORNERS", "POSTAGE_STAMP", "X_SHAPE",
  "T_SHAPE", "L_SHAPE", "OUTER_FRAME", "FULL_HOUSE", "HORIZONTAL_LINE",
  "VERTICAL_LINE", "DIAGONAL_LINES", "LINE_TOUCHING_FREE",
  "LINE_NOT_TOUCHING_FREE", "NON_CROSSING_LINES", "SQUARE", "RECTANGLE",
  "SMALL_X", "SMALL_T", "SMALL_CROSS", "BIG_T", "BIG_Z", "BIG_H",
  "BIG_L", "BIG_U", "HALF_HOUSE", "BIG_O", "CORNER_BALLS",
] as const;
type PrizePattern = (typeof prizePatterns)[number];
type PrizeSettlement = "PLAYER_WALLET" | "AGENT_CASH";

interface PrizeDraft {
  prizeType: PrizePattern;
  amount: string;
  settlementPath: PrizeSettlement;
}

interface SessionDraft {
  roomId: string;
  sessionName: string;
  scheduledDate: string;
  scheduledTime: string;
  state: string;
  configuredPrizes: PrizeDraft[];
}

function createInitialDraft(): SessionDraft {
  return {
    roomId: "",
    sessionName: "",
    scheduledDate: "",
    scheduledTime: "",
    state: "OPEN",
    configuredPrizes: [],
  };
}

const initialSessionStates = ["DRAFT", "OPEN"];

export function SessionCreatePage() {
  const location = useLocation();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const roomsQuery = useQuery({ queryKey: ["rooms"], queryFn: listRooms });
  const rooms = (roomsQuery.data ?? []).filter((room) => room.active);
  const calendarPreference = currentCalendarPreference();
  const [draft, setDraft] = useState<SessionDraft>(createInitialDraft);
  const [discardOpen, setDiscardOpen] = useState(false);
  const selectedRoom = rooms.find((room) => room.id === Number(draft.roomId));
  const hasCashPrize = draft.configuredPrizes.some((prize) => prize.settlementPath === "AGENT_CASH");
  const scheduleTime = selectedRoom
    ? resolveEthiopianRoomScheduleTime(draft.scheduledDate, draft.scheduledTime, selectedRoom.timezone)
    : { instant: null, error: null };
  const ethiopianTime = ethiopianTimeParts(draft.scheduledTime) ?? {
    hour: 12,
    minute: 0,
    period: "daytime" as EthiopianTimePeriod,
  };

  const createMutation = useMutation({
    mutationFn: (body: SessionDraft) => {
      const room = rooms.find((candidate) => candidate.id === Number(body.roomId));
      if (!room) throw new Error("Choose an active room before creating a session.");
      const resolvedStart = resolveEthiopianRoomScheduleTime(body.scheduledDate, body.scheduledTime, room.timezone);
      if (!resolvedStart.instant) throw new Error(resolvedStart.error);
      return createSession(Number(body.roomId), {
        sessionName: body.sessionName,
        scheduledStartAt: resolvedStart.instant.toISOString(),
        state: body.state,
        configuredPatterns: body.configuredPrizes.map((prize) => prize.prizeType),
        configuredPrizes: body.configuredPrizes.map((prize) => ({
          prizeType: prize.prizeType,
          amount: Number(prize.amount),
          settlementPath: prize.settlementPath,
        })),
      });
    },
    onSuccess: async (session) => {
      await queryClient.invalidateQueries({ queryKey: ["sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      navigate(`/sessions/${session.id}${location.search}`);
    },
  });

  const isDraftValid = Boolean(
    selectedRoom
    && draft.sessionName.trim()
    && draft.sessionName.trim().length <= 120
    && draft.scheduledDate
    && draft.scheduledTime
    && draft.configuredPrizes.length > 0
    && new Set(draft.configuredPrizes.map((prize) => prize.prizeType)).size ===
      draft.configuredPrizes.length
    && draft.configuredPrizes.every(
      (prize) =>
        prize.settlementPath === "PLAYER_WALLET" &&
        /^\d+(\.\d{1,2})?$/.test(prize.amount) &&
        Number(prize.amount) >= (selectedRoom?.sessionCapacity ?? 1) / 100,
    )
    && scheduleTime.instant
    && initialSessionStates.includes(draft.state),
  );
  const hasUnsavedChanges = Boolean(draft.roomId || draft.sessionName.trim() || draft.scheduledDate || draft.scheduledTime || draft.state !== "OPEN");

  function cancelCreation() {
    if (hasUnsavedChanges) setDiscardOpen(true);
    else navigate(`/sessions${location.search}`);
  }

  function updateDraft(next: Partial<SessionDraft>) {
    setDraft((current) => ({ ...current, ...next }));
    if (createMutation.error) createMutation.reset();
  }

  function updateEthiopianTime(next: Partial<typeof ethiopianTime>) {
    const localTime = ethiopianTimeToLocal(
      next.hour ?? ethiopianTime.hour,
      next.minute ?? ethiopianTime.minute,
      next.period ?? ethiopianTime.period,
    );
    if (localTime) updateDraft({ scheduledTime: localTime });
  }

  function updatePrize(index: number, next: Partial<PrizeDraft>) {
    updateDraft({
      configuredPrizes: draft.configuredPrizes.map((prize, prizeIndex) =>
        prizeIndex === index ? { ...prize, ...next } : prize,
      ),
    });
  }

  function addPrize() {
    const nextPattern = prizePatterns.find(
      (pattern) =>
        !draft.configuredPrizes.some((prize) => prize.prizeType === pattern),
    );
    if (!nextPattern) return;
    updateDraft({
      configuredPrizes: [
        ...draft.configuredPrizes,
        {
          prizeType: nextPattern,
          amount: String(Math.max(1, selectedRoom?.sessionCapacity ?? 100) / 100),
          settlementPath: "PLAYER_WALLET",
        },
      ],
    });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isDraftValid && !createMutation.isPending) {
      createMutation.mutate(draft);
    }
  }

  if (roomsQuery.isLoading) {
    return <StatusState title={t("Loading rooms")} loading message={t("Fetching room catalog")} />;
  }

  if (roomsQuery.error) {
    return (
      <StatusState
        title={t("Unable to load rooms")}
        message={t(getUserFacingErrorMessage(roomsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => roomsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <Typography variant="h4">{t("Create session")}</Typography>
        <Button onClick={cancelCreation} variant="outlined">
          {t("Back to sessions")}
        </Button>
      </Box>

      <Paper variant="outlined" sx={{ borderRadius: 3, overflow: "hidden" }}>
        <Box sx={{ px: { xs: 2, md: 3 }, py: 2.5, borderBottom: "1px solid", borderColor: "divider", bgcolor: "grey.50" }}>
          <Typography variant="h5">{t("Session details")}</Typography>
        </Box>
        <Box component="form" onSubmit={handleSubmit} sx={{ px: { xs: 2, md: 3 }, py: { xs: 2, md: 3 } }}>
          <Stack spacing={3}>
            {createMutation.error ? (
              <Alert severity="error">
                {t(getUserFacingErrorMessage(createMutation.error) ?? "Something went wrong. Please try again.")}
              </Alert>
            ) : null}
            {rooms.length === 0 ? (
              <Alert
                severity="info"
                action={
                  <Button component={RouterLink} to={roomsQuery.data?.length ? "/rooms" : "/rooms/new"} color="inherit" size="small">
                    {t(roomsQuery.data?.length ? "Manage rooms" : "Create room")}
                  </Button>
                }
              >
                {t(roomsQuery.data?.length
                  ? "No active rooms are available. Activate a room before scheduling a session."
                  : "No rooms are available. Create a room before scheduling a session.")}
              </Alert>
            ) : null}

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  select
                  label={t("Room")}
                  value={draft.roomId}
                  onChange={(event) => {
                    const roomId = event.target.value;
                    const room = rooms.find((candidate) => candidate.id === Number(roomId));
                    const defaults = room
                      ? getDefaultEthiopianRoomScheduleTime(room.timezone)
                      : { scheduledDate: "", scheduledTime: "" };
                    updateDraft({
                      roomId,
                      configuredPrizes: room
                        ? room.configuredPrizes
                            .filter((prize) =>
                              prizePatterns.includes(
                                prize.prizeType as PrizePattern,
                              ),
                            )
                            .map((prize) => ({
                              prizeType: prize.prizeType as PrizePattern,
                              amount: String(prize.amount),
                              settlementPath:
                                prize.settlementPath === "AGENT_CASH"
                                  ? "AGENT_CASH"
                                  : "PLAYER_WALLET",
                            }))
                        : [],
                      scheduledDate:
                        draft.scheduledDate || defaults.scheduledDate,
                      scheduledTime:
                        draft.scheduledTime || defaults.scheduledTime,
                    });
                  }}
                  fullWidth
                  required
                >
                  {rooms.map((room) => (
                    <MenuItem value={room.id} key={room.id}>
                      {room.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label={t("Session name")}
                  value={draft.sessionName}
                  onChange={(event) => updateDraft({ sessionName: event.target.value })}
                  fullWidth
                  required
                  inputProps={{ maxLength: 120 }}
                  error={draft.sessionName.trim().length > 120}
                  helperText={draft.sessionName.trim().length > 120 ? t("Session name must be 120 characters or fewer.") : undefined}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                {calendarPreference === "ETHIOPIAN" ? (
                  <EthiopianDatePicker
                    label={t("Ethiopian calendar date")}
                    value={draft.scheduledDate}
                    onChange={(scheduledDate) => updateDraft({ scheduledDate })}
                    required
                    error={Boolean(selectedRoom && draft.scheduledDate && scheduleTime.error)}
                    helperText={selectedRoom
                      ? scheduleTime.error
                        ? t(scheduleTime.error)
                        : `${t("Room local time")} (${selectedRoom.timezone})`
                      : t("Choose a room to set its local time zone.")}
                  />
                ) : (
                  <TextField
                    label={t("Gregorian calendar date")}
                    type="date"
                    value={ethiopianDateInputToGregorianDate(draft.scheduledDate) ?? ""}
                    onChange={(event) => updateDraft({ scheduledDate: event.target.value ? formatEthiopianDateInput(`${event.target.value}T12:00:00Z`) : "" })}
                    fullWidth
                    required
                    error={Boolean(selectedRoom && draft.scheduledDate && scheduleTime.error)}
                    helperText={selectedRoom
                      ? scheduleTime.error
                        ? t(scheduleTime.error)
                        : `${t("Room local time")} (${selectedRoom.timezone})`
                      : t("Choose a room to set its local time zone.")}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                )}
              </Grid>
              <Grid item xs={12} md={6}>
                <Stack spacing={1}>
                  <Typography variant="caption" color="text.secondary">
                    {t("Scheduled time")} *
                  </Typography>
                  <Stack direction="row" spacing={1}>
                    <TextField
                      select
                      label={t("Hour")}
                      value={ethiopianTime.hour}
                      onChange={(event) => updateEthiopianTime({ hour: Number(event.target.value) })}
                      fullWidth
                    >
                      {Array.from({ length: 12 }, (_, index) => index + 1).map((hour) => (
                        <MenuItem key={hour} value={hour}>{hour}</MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      select
                      label={t("Minute")}
                      value={ethiopianTime.minute}
                      onChange={(event) => updateEthiopianTime({ minute: Number(event.target.value) })}
                      fullWidth
                    >
                      {Array.from({ length: 60 }, (_, minute) => minute).map((minute) => (
                        <MenuItem key={minute} value={minute}>{String(minute).padStart(2, "0")}</MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      select
                      label={t("Period")}
                      value={ethiopianTime.period}
                      onChange={(event) => updateEthiopianTime({ period: event.target.value as EthiopianTimePeriod })}
                      fullWidth
                    >
                      <MenuItem value="daytime">{t("Daytime")}</MenuItem>
                      <MenuItem value="nighttime">{t("Nighttime")}</MenuItem>
                    </TextField>
                  </Stack>
                </Stack>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  select
                  label={t("Initial state")}
                  value={draft.state}
                  onChange={(event) => updateDraft({ state: event.target.value })}
                  fullWidth
                  required
                >
                  {initialSessionStates.map((state) => (
                    <MenuItem key={state} value={state}>
                      {translateAdminStatus(state, t)}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
            </Grid>

            {selectedRoom ? (
              <Stack spacing={1.5}>
                <Box
                  display="flex"
                  alignItems="center"
                  justifyContent="space-between"
                  gap={2}
                  flexWrap="wrap"
                >
                  <Box>
                    <Typography variant="h6">{t("Session prizes")}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {t(
                        "Prizes are copied from the room and can be edited for this session.",
                      )}
                    </Typography>
                  </Box>
                  {hasCashPrize ? <Alert severity="warning">{t("Cash prize payouts are unavailable for new games. Change this prize to Player wallet. Existing cash entitlements remain pending and unpaid for manual review.")}</Alert> : null}
                  <Button
                    size="small"
                    onClick={addPrize}
                    disabled={draft.configuredPrizes.length >= prizePatterns.length}
                  >
                    {t("Add prize")}
                  </Button>
                </Box>
                {draft.configuredPrizes.map((prize, index) => (
                  <Paper
                    key={`${prize.prizeType}-${index}`}
                    variant="outlined"
                    sx={{ p: 1.5 }}
                  >
                    <Grid container spacing={1.5} alignItems="center">
                      <Grid item xs={12} sm={4}>
                        <TextField
                          select
                          label={t("Pattern")}
                          value={prize.prizeType}
                          onChange={(event) =>
                            updatePrize(index, {
                              prizeType: event.target.value as PrizePattern,
                            })
                          }
                          fullWidth
                        >
                          {prizePatterns.map((pattern) => (
                            <MenuItem
                              key={pattern}
                              value={pattern}
                              disabled={draft.configuredPrizes.some(
                                (other, otherIndex) =>
                                  otherIndex !== index &&
                                  other.prizeType === pattern,
                              )}
                            >
                              {translateAdminValue(pattern, t)}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={3}>
                        <TextField
                          label={`${t("Prize amount")} (${selectedRoom.currency})`}
                          type="number"
                          value={prize.amount}
                          onChange={(event) =>
                            updatePrize(index, { amount: event.target.value })
                          }
                          inputProps={{
                            min: selectedRoom.sessionCapacity / 100,
                            step: 0.01,
                          }}
                          error={
                            !/^\d+(\.\d{1,2})?$/.test(prize.amount) ||
                            Number(prize.amount) <
                              selectedRoom.sessionCapacity / 100
                          }
                          helperText={t("Minimum {{amount}} {{currency}}", {
                            amount: (selectedRoom.sessionCapacity / 100).toFixed(
                              2,
                            ),
                            currency: selectedRoom.currency,
                          })}
                          fullWidth
                        />
                      </Grid>
                      <Grid item xs={12} sm={3}>
                        <TextField
                          select
                          label={t("Settlement")}
                          value={prize.settlementPath}
                          onChange={(event) =>
                            updatePrize(index, {
                              settlementPath: event.target
                                .value as PrizeSettlement,
                            })
                          }
                          fullWidth
                        >
                          <MenuItem value="PLAYER_WALLET">
                            {t("Player wallet")}
                          </MenuItem>
                          <MenuItem value="AGENT_CASH" disabled>
                            {t("Agent cash (unavailable)")}
                          </MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={2}>
                        <Button
                          color="error"
                          disabled={draft.configuredPrizes.length <= 1}
                          onClick={() =>
                            updateDraft({
                              configuredPrizes: draft.configuredPrizes.filter(
                                (_, prizeIndex) => prizeIndex !== index,
                              ),
                            })
                          }
                        >
                          {t("Remove")}
                        </Button>
                      </Grid>
                    </Grid>
                  </Paper>
                ))}
              </Stack>
            ) : null}

            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button onClick={cancelCreation}>
                {t("Cancel")}
              </Button>
              <Button type="submit" variant="contained" disabled={!isDraftValid || createMutation.isPending}>
                {createMutation.isPending ? t("Creating…") : t("Create session")}
              </Button>
            </Stack>
          </Stack>
        </Box>
      </Paper>
      <ConfirmDialog
        open={discardOpen}
        title={t("Discard unsaved changes?")}
        description={t("Your changes have not been saved. Discard them?")}
        confirmLabel={t("Discard")}
        cancelLabel={t("Keep editing")}
        onCancel={() => setDiscardOpen(false)}
        onConfirm={() => navigate(`/sessions${location.search}`)}
      />
    </Stack>
  );
}
