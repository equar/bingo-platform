import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Box, Button, Grid, MenuItem, Paper, Stack, TextField, Typography } from "@mui/material";
import { Link as RouterLink, useLocation, useNavigate, useParams } from "react-router-dom";
import { listRooms, sessionDetail, updateSession, updateSessionState } from "../../api/admin";
import type { BingoPatternConfig } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { EthiopianDatePicker } from "../../components/EthiopianDatePicker";
import { translateAdminStatus, translateAdminValue } from "../../i18n";
import { currentCalendarPreference, ethiopianDateInputToGregorianDate, formatEthiopianDateInput } from "../../utils/ethiopianDateTime";
import { formatDateTimeLocal, resolveRoomScheduleTime } from "./sessionScheduleTime";

const patterns = ["LINE", "TWO_LINES", "FOUR_CORNERS", "POSTAGE_STAMP", "X_SHAPE", "T_SHAPE", "L_SHAPE", "OUTER_FRAME", "FULL_HOUSE", "HORIZONTAL_LINE", "VERTICAL_LINE", "DIAGONAL_LINES", "LINE_TOUCHING_FREE", "LINE_NOT_TOUCHING_FREE", "NON_CROSSING_LINES", "SQUARE", "RECTANGLE", "SMALL_X", "SMALL_T", "SMALL_CROSS", "BIG_T", "BIG_Z", "BIG_H", "BIG_L", "BIG_U", "HALF_HOUSE", "BIG_O", "CORNER_BALLS"];
type PrizeDraft = Omit<BingoPatternConfig, "amount"> & { amount: string };

export function SessionEditPage() {
  const { t } = useTranslation();
  const { sessionId } = useParams();
  const id = Number(sessionId);
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const sessionQuery = useQuery({ queryKey: ["session", id], queryFn: () => sessionDetail(id), enabled: Number.isFinite(id) });
  const roomsQuery = useQuery({ queryKey: ["rooms"], queryFn: listRooms });
  const session = sessionQuery.data;
  const room = roomsQuery.data?.find((item) => item.id === session?.roomId);
  const [name, setName] = useState("");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
  const [state, setState] = useState("DRAFT");
  const [prizes, setPrizes] = useState<PrizeDraft[]>([]);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!session || !room) return;
    const local = formatDateTimeLocal(new Date(session.scheduledStartAt), room.timezone);
    setName(session.sessionName);
    setDate(currentCalendarPreference() === "ETHIOPIAN"
      ? formatEthiopianDateInput(`${local.slice(0, 10)}T12:00:00Z`)
      : local.slice(0, 10));
    setTime(local.slice(11, 16));
    setState(session.state);
    setPrizes((session.configuredPrizes ?? []).map((prize) => ({ ...prize, amount: String(prize.amount) })));
  }, [session, room]);

  const start = useMemo(() => {
    if (!room || !date || !time) return null;
    const gregorian = currentCalendarPreference() === "ETHIOPIAN" ? ethiopianDateInputToGregorianDate(date) : date;
    return gregorian ? resolveRoomScheduleTime(`${gregorian}T${time}`, room.timezone) : null;
  }, [room, date, time]);
  const valid = Boolean(session && room && name.trim() && name.trim().length <= 120 && start?.instant
    && prizes.length > 0 && new Set(prizes.map((prize) => prize.prizeType)).size === prizes.length
    && prizes.every((prize) => Number(prize.amount) >= session.sessionCapacity / 100 && prize.settlementPath === "PLAYER_WALLET"));
  const hasCashPrize = prizes.some((prize) => prize.settlementPath === "AGENT_CASH");

  const saveMutation = useMutation({
    mutationFn: () => updateSession(id, {
      sessionName: name.trim(),
      scheduledStartAt: start!.instant!.toISOString(),
      state,
      configuredPatterns: prizes.map((prize) => prize.prizeType),
      configuredPrizes: prizes.map((prize) => ({ ...prize, amount: Number(prize.amount) })),
    }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["session", id] }),
        queryClient.invalidateQueries({ queryKey: ["sessions"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
      navigate(`/sessions/${id}${location.search}`);
    },
  });
  const cancelMutation = useMutation({
    mutationFn: () => updateSessionState(id, "CANCELLED"),
    onSuccess: async () => {
      await Promise.all([queryClient.invalidateQueries({ queryKey: ["sessions"] }), queryClient.invalidateQueries({ queryKey: ["session", id] })]);
      navigate(`/sessions${location.search}`);
    },
    onError: (error) => setCancelError(getUserFacingErrorMessage(error) ?? "Unable to cancel this session."),
  });

  if (sessionQuery.isLoading || roomsQuery.isLoading) return <StatusState title={t("Loading session")} loading message={t("Fetching session details")} />;
  if (sessionQuery.error || roomsQuery.error || !session || !room) return <StatusState title={t("Unable to load session")} message={t(getUserFacingErrorMessage(sessionQuery.error ?? roomsQuery.error) ?? "Session details could not be loaded.")} actionLabel={t("Back to sessions")} onAction={() => navigate(`/sessions${location.search}`)} />;
  if (session.state !== "DRAFT" && session.state !== "OPEN") return <StatusState title={t("Session cannot be edited")} message={t("Only draft or open sessions can be edited.")} actionLabel={t("Back to sessions")} onAction={() => navigate(`/sessions${location.search}`)} />;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!valid || saveMutation.isPending) return;
    setFormError(null);
    saveMutation.mutate();
  }
  function changePrize(index: number, changes: Partial<PrizeDraft>) {
    setPrizes((current) => current.map((prize, i) => i === index ? { ...prize, ...changes } : prize));
  }

  return <Stack spacing={3}>
    <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
      <Typography variant="h4">{t("Edit session")}</Typography>
      <Button component={RouterLink} to={`/sessions${location.search}`} variant="outlined">{t("Back to sessions")}</Button>
    </Box>
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 }, borderRadius: 3 }}>
      <Box component="form" onSubmit={submit}>
        <Stack spacing={3}>
          {saveMutation.error || formError ? <Alert severity="error">{t(formError ?? getUserFacingErrorMessage(saveMutation.error) ?? "Something went wrong. Please try again.")}</Alert> : null}
          <Typography variant="body2" color="text.secondary">{t("Room")}: {session.roomName} · {t("Status")}: {translateAdminStatus(session.state, t)}</Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}><TextField label={t("Session name")} value={name} onChange={(event) => setName(event.target.value)} fullWidth required inputProps={{ maxLength: 120 }} /></Grid>
            <Grid item xs={12} md={6}><TextField select label={t("Status")} value={state} onChange={(event) => setState(event.target.value)} fullWidth>
              {(session.state === "DRAFT" ? ["DRAFT", "OPEN"] : ["OPEN"]).map((value) => <MenuItem key={value} value={value}>{translateAdminStatus(value, t)}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={12} md={6}>{currentCalendarPreference() === "ETHIOPIAN"
              ? <EthiopianDatePicker label={t("Ethiopian calendar date")} value={date} onChange={setDate} required helperText={`${t("Room local time")} (${room.timezone})`} />
              : <TextField label={t("Gregorian calendar date")} type="date" value={date} onChange={(event) => setDate(event.target.value)} fullWidth required slotProps={{ inputLabel: { shrink: true } }} />}</Grid>
            <Grid item xs={12} md={6}><TextField label={t("Scheduled time")} type="time" value={time} onChange={(event) => setTime(event.target.value)} fullWidth required slotProps={{ inputLabel: { shrink: true } }} helperText={`${t("Room local time")} (${room.timezone})`} /></Grid>
          </Grid>
          {start && !start.instant ? <Alert severity="error">{t(start.error)}</Alert> : null}
          <Stack spacing={1.5}>
            <Typography variant="h6">{t("Session prizes")}</Typography>
            {hasCashPrize ? <Alert severity="warning">{t("Cash prize payouts are unavailable for new games. Change this prize to Player wallet. Existing cash entitlements remain pending and unpaid for manual review.")}</Alert> : null}
            {prizes.map((prize, index) => <Grid container spacing={1.5} key={`${prize.prizeType}-${index}`}>
              <Grid item xs={12} sm={4}><TextField select label={t("Pattern")} value={prize.prizeType} fullWidth onChange={(event) => changePrize(index, { prizeType: event.target.value })}>
                {patterns.map((pattern) => <MenuItem key={pattern} value={pattern} disabled={prizes.some((item, other) => other !== index && item.prizeType === pattern)}>{translateAdminValue(pattern, t)}</MenuItem>)}
              </TextField></Grid>
              <Grid item xs={12} sm={4}><TextField label={`${t("Prize amount")} (${session.currency})`} type="number" value={prize.amount} inputProps={{ min: session.sessionCapacity / 100, step: 0.01 }} onChange={(event) => changePrize(index, { amount: event.target.value })} fullWidth /></Grid>
              <Grid item xs={12} sm={4}><TextField select label={t("Settlement")} value={prize.settlementPath} onChange={(event) => changePrize(index, { settlementPath: event.target.value })} fullWidth><MenuItem value="PLAYER_WALLET">{t("Player wallet")}</MenuItem><MenuItem value="AGENT_CASH" disabled>{t("Agent cash (unavailable)")}</MenuItem></TextField></Grid>
            </Grid>)}
          </Stack>
          <Stack direction="row" spacing={1} justifyContent="space-between" flexWrap="wrap">
            <Button color="error" variant="outlined" onClick={() => { setCancelError(null); setCancelOpen(true); }}>{t("Cancel session")}</Button>
            <Stack direction="row" spacing={1}><Button onClick={() => navigate(`/sessions${location.search}`)}>{t("Discard")}</Button><Button type="submit" variant="contained" disabled={!valid || saveMutation.isPending}>{saveMutation.isPending ? t("Saving…") : t("Save changes")}</Button></Stack>
          </Stack>
        </Stack>
      </Box>
    </Paper>
    <ConfirmDialog open={cancelOpen} title={t("Cancel session?")} description={t("The session will be cancelled. Any eligible ticket refunds will be processed.")} confirmLabel={t("Cancel session")} cancelLabel={t("Keep session")} confirming={cancelMutation.isPending} error={cancelError ?? undefined} onCancel={() => setCancelOpen(false)} onConfirm={() => cancelMutation.mutate()} />
  </Stack>;
}
