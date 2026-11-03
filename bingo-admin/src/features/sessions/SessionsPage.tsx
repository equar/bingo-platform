import { useTranslation } from "react-i18next";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { createSession, listRooms, listSessions, updateSessionState } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { translateAdminStatus } from "../../i18n";

interface SessionDraft {
  roomId: string;
  sessionName: string;
  scheduledStartAt: string;
  state: string;
}

const initialDraft: SessionDraft = {
  roomId: "",
  sessionName: "",
  scheduledStartAt: new Date().toISOString().slice(0, 16),
  state: "OPEN",
};

const allowedStates: Record<string, string[]> = {
  DRAFT: ["OPEN", "CANCELLED"],
  OPEN: ["STARTED", "CANCELLED"],
  STARTED: ["DRAWING", "CLOSED", "CANCELLED"],
  DRAWING: ["CLOSED", "CANCELLED"],
  CLOSED: ["SETTLED", "CANCELLED"],
};

export function SessionsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const roomsQuery = useQuery({ queryKey: ["rooms"], queryFn: listRooms });
  const [draft, setDraft] = useState<SessionDraft | null>(null);
  const [search, setSearch] = useState("");
  const [stateFilter, setStateFilter] = useState("ALL");
  const [roomFilter, setRoomFilter] = useState("ALL");
  const [startFilter, setStartFilter] = useState("ALL");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingTransition, setPendingTransition] = useState<{ sessionId: number; nextState: string } | null>(null);
  const normalizedSearch = search.trim();
  const sessionsQuery = useQuery({
    queryKey: ["sessions", normalizedSearch, stateFilter, roomFilter, startFilter, page, pageSize],
    queryFn: () =>
      listSessions(
        page,
        pageSize,
        normalizedSearch || undefined,
        stateFilter === "ALL" ? undefined : stateFilter,
        roomFilter === "ALL" ? undefined : Number(roomFilter),
        startFilter === "ALL" ? undefined : startFilter === "UPCOMING",
      ),
    placeholderData: keepPreviousData,
  });

  const createMutation = useMutation({
    mutationFn: (body: SessionDraft) =>
      createSession(Number(body.roomId), {
        sessionName: body.sessionName,
        scheduledStartAt: new Date(body.scheduledStartAt).toISOString(),
        state: body.state,
      }),
    onSuccess: async (session) => {
      await queryClient.invalidateQueries({ queryKey: ["sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setDraft(null);
      navigate(`/sessions/${session.id}`);
    },
  });

  const transitionMutation = useMutation({
    mutationFn: ({ sessionId, nextState }: { sessionId: number; nextState: string }) =>
      updateSessionState(sessionId, nextState),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setPendingTransition(null);
      setConfirmOpen(false);
    },
  });

  const rooms = roomsQuery.data ?? [];
  const sessions = sessionsQuery.data?.content ?? [];
  const title = draft ? "Create session" : "";

  if (sessionsQuery.isLoading) {
    return <StatusState title={t("Loading sessions")} loading message={t("Fetching session list")} />;
  }

  if (sessionsQuery.error) {
    return (
      <StatusState
        title={t("Unable to load sessions")}
        message={t(getUserFacingErrorMessage(sessionsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => sessionsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">{t("Sessions")}</Typography>
          <Typography color="text.secondary">{t("Create, activate, and open live draw views.")}</Typography>
        </div>
        <Button variant="contained" onClick={() => setDraft({ ...initialDraft })}>
          {t("New session")}</Button>
      </Box>

      <Stack spacing={1.5}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
            <TextField
              label={t("Search sessions")}
              placeholder={t("Name, room, or ID")}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 2, minWidth: { md: 260 } }}
            />
            <TextField
              select
              label={t("Status")}
              value={stateFilter}
              onChange={(event) => {
                setStateFilter(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">{t("All statuses")}</MenuItem>
              {["DRAFT", "OPEN"].map((state) => (
                <MenuItem key={state} value={state}>
                  {translateAdminStatus(state, t)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label={t("Room")}
              value={roomFilter}
              onChange={(event) => {
                setRoomFilter(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">{t("All rooms")}</MenuItem>
              {rooms.map((room) => (
                <MenuItem key={room.id} value={String(room.id)}>
                  {room.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label={t("Start date")}
              value={startFilter}
              onChange={(event) => {
                setStartFilter(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">{t("Any time")}</MenuItem>
              <MenuItem value="UPCOMING">{t("Upcoming")}</MenuItem>
              <MenuItem value="PAST">{t("Past")}</MenuItem>
            </TextField>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {t("Showing")}{" "}{sessions.length} {t("of")}{" "}{sessionsQuery.data?.totalElements ?? 0} {t("sessions")}</Typography>
          {sessionsQuery.isFetching && !sessionsQuery.isLoading ? (
            <Alert severity="info">{t("Updating session results...")}</Alert>
          ) : null}
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" sx={{ minWidth: 1050 }} aria-label={t("Sessions")}>
            <TableHead>
              <TableRow>
                <TableCell>{t("Session")}</TableCell>
                <TableCell>{t("Room")}</TableCell>
                <TableCell>{t("Ticket price")}</TableCell>
                <TableCell>{t("Status")}</TableCell>
                <TableCell align="right">{t("Tickets")}</TableCell>
                <TableCell align="right">{t("Draws")}</TableCell>
                <TableCell>{t("Scheduled start")}</TableCell>
                <TableCell>{t("State actions")}</TableCell>
                <TableCell align="right">{t("View")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sessions.map((session) => (
                <TableRow key={session.id} hover>
                  <TableCell component="th" scope="row">
                    <Link component={RouterLink} to={`/sessions/${session.id}`} fontWeight={600}>
                      {session.sessionName}
                    </Link>
                  </TableCell>
                  <TableCell>{session.roomName}</TableCell>
                  <TableCell sx={{ whiteSpace: "nowrap" }}>
                    {session.currency} {session.ticketPrice}
                  </TableCell>
                  <TableCell>
                    <Chip label={translateAdminStatus(session.state, t)} color="primary" size="small" />
                  </TableCell>
                  <TableCell align="right">{session.ticketCount}</TableCell>
                  <TableCell align="right">{session.drawCount}</TableCell>
                  <TableCell sx={{ whiteSpace: "nowrap" }}>
                    {new Date(session.scheduledStartAt).toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.75}>
                      {(allowedStates[session.state] ?? []).map((nextState) => (
                        <Button
                          key={nextState}
                          size="small"
                          variant="outlined"
                          onClick={() => {
                            setPendingTransition({ sessionId: session.id, nextState });
                            setConfirmOpen(true);
                          }}
                        >
                          {translateAdminStatus(nextState, t)}
                        </Button>
                      ))}
                      {(allowedStates[session.state] ?? []).length === 0 ? (
                        <Typography variant="body2" color="text.secondary">
                          {t("—")}</Typography>
                      ) : null}
                    </Stack>
                  </TableCell>
                  <TableCell align="right">
                    <Button size="small" variant="text" onClick={() => navigate(`/sessions/${session.id}`)}>
                      {t("Open live view")}</Button>
                  </TableCell>
                </TableRow>
              ))}
              {sessionsQuery.data && sessions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                    {t("No sessions match your search and filters.")}</TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        <AdminTablePagination
          component="div"
          count={sessionsQuery.data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => {
            setPageSize(Number(event.target.value));
            setPage(0);
          }}
          rowsPerPageOptions={[10, 20, 50]}
        />
      </Stack>

      <Dialog open={Boolean(draft)} onClose={() => setDraft(null)} fullWidth maxWidth="sm">
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              select
              label={t("Room")}
              value={draft?.roomId ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, roomId: event.target.value })}
              fullWidth
            >
              {rooms.map((room) => (
                <MenuItem value={room.id} key={room.id}>
                  {room.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label={t("Session name")}
              value={draft?.sessionName ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, sessionName: event.target.value })}
              fullWidth
            />
            <TextField
              label={t("Scheduled start")}
              type="datetime-local"
              value={draft?.scheduledStartAt ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, scheduledStartAt: event.target.value })}
              fullWidth
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              select
              label={t("Initial state")}
              value={draft?.state ?? "OPEN"}
              onChange={(event) => draft && setDraft({ ...draft, state: event.target.value })}
              fullWidth
            >
              {["DRAFT", "OPEN", "STARTED", "DRAWING", "CLOSED", "SETTLED", "CANCELLED"].map((state) => (
                <MenuItem key={state} value={state}>
                  {translateAdminStatus(state, t)}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraft(null)}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => draft && createMutation.mutate(draft)}
            disabled={createMutation.isPending}
          >
            {t("Create")}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        title={t("Change session state")}
        description={t("Transition the session to {{status}}?", {
          status: translateAdminStatus(pendingTransition?.nextState ?? "", t),
        })}
        confirmLabel={t("Confirm")}
        cancelLabel={t("Back")}
        confirming={transitionMutation.isPending}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => pendingTransition && transitionMutation.mutate(pendingTransition)}
      />
    </Stack>
  );
}
