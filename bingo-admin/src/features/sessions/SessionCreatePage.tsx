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
import { translateAdminStatus } from "../../i18n";
import { getDefaultEthiopianRoomScheduleTime, resolveEthiopianRoomScheduleTime } from "./sessionScheduleTime";

interface SessionDraft {
  roomId: string;
  sessionName: string;
  scheduledDate: string;
  scheduledTime: string;
  state: string;
}

function createInitialDraft(): SessionDraft {
  return {
    roomId: "",
    sessionName: "",
    scheduledDate: "",
    scheduledTime: "",
    state: "OPEN",
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
  const [draft, setDraft] = useState<SessionDraft>(createInitialDraft);
  const [discardOpen, setDiscardOpen] = useState(false);
  const selectedRoom = rooms.find((room) => room.id === Number(draft.roomId));
  const scheduleTime = selectedRoom
    ? resolveEthiopianRoomScheduleTime(draft.scheduledDate, draft.scheduledTime, selectedRoom.timezone)
    : { instant: null, error: null };

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
                      scheduledDate: draft.scheduledDate || defaults.scheduledDate,
                      scheduledTime: draft.scheduledTime || defaults.scheduledTime,
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
                <TextField
                  label={t("Ethiopian calendar date")}
                  placeholder="2019-01-01"
                  value={draft.scheduledDate}
                  onChange={(event) => updateDraft({ scheduledDate: event.target.value })}
                  fullWidth
                  required
                  error={Boolean(selectedRoom && draft.scheduledDate && scheduleTime.error)}
                  helperText={selectedRoom
                    ? scheduleTime.error
                      ? t(scheduleTime.error)
                      : `${t("Room local time")} (${selectedRoom.timezone})`
                    : t("Choose a room to set its local time zone.")}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label={t("Scheduled time")}
                  type="time"
                  value={draft.scheduledTime}
                  onChange={(event) => updateDraft({ scheduledTime: event.target.value })}
                  fullWidth
                  required
                  InputLabelProps={{ shrink: true }}
                />
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
