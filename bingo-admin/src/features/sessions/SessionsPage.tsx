import { useTranslation } from "react-i18next";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Chip,
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
import { Link as RouterLink, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { listRooms, listSessions, updateSessionState } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { translateAdminStatus } from "../../i18n";

const allowedStates: Record<string, string[]> = {
  DRAFT: ["OPEN", "CANCELLED"],
  OPEN: ["STARTED", "CANCELLED"],
  STARTED: ["DRAWING", "CLOSED"],
  DRAWING: ["CLOSED"],
  CLOSED: ["SETTLED"],
};
const sessionStates = ["DRAFT", "OPEN", "STARTED", "DRAWING", "CLOSED", "SETTLED", "CANCELLED"];

function availableTransitions(session: { state: string; ticketCount: number }) {
  const transitions = allowedStates[session.state] ?? [];
  return session.ticketCount > 0 ? transitions.filter((state) => state !== "CANCELLED") : transitions;
}

export function SessionsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = usePrivateListSearch("sessions");
  const roomsQuery = useQuery({ queryKey: ["rooms"], queryFn: listRooms });
  const stateParam = searchParams.get("status") ?? "ALL";
  const stateFilter = sessionStates.includes(stateParam) ? stateParam : "ALL";
  const roomParam = searchParams.get("room") ?? "ALL";
  const roomFilter = roomParam === "ALL" || /^[1-9]\d*$/.test(roomParam) ? roomParam : "ALL";
  const startParam = searchParams.get("start") ?? "ALL";
  const startFilter = ["ALL", "UPCOMING", "PAST"].includes(startParam) ? startParam : "ALL";
  const pageParam = Number(searchParams.get("page") ?? 0);
  const page = Number.isSafeInteger(pageParam) ? Math.max(0, pageParam) : 0;
  const pageSizeValue = Number(searchParams.get("size") ?? 20);
  const pageSize = [10, 20, 50].includes(pageSizeValue) ? pageSizeValue : 20;
  const updateListState = (updates: Record<string, string>) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      Object.entries(updates).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
      return next;
    }, { replace: true });
  };
  // Remove legacy free-text values from the URL; session names may contain personal data.
  useEffect(() => {
    if (!searchParams.has("search")) return;
    const safeParams = new URLSearchParams(searchParams);
    safeParams.delete("search");
    setSearchParams(safeParams, { replace: true });
  }, [searchParams, setSearchParams]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingTransition, setPendingTransition] = useState<{ sessionId: number; nextState: string } | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);
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

  const transitionMutation = useMutation({
    mutationFn: ({ sessionId, nextState }: { sessionId: number; nextState: string }) =>
      updateSessionState(sessionId, nextState),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setPendingTransition(null);
      setConfirmOpen(false);
    },
    onError: (error) => setTransitionError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const rooms = roomsQuery.data ?? [];
  const sessions = sessionsQuery.data?.content ?? [];

  if (sessionsQuery.isLoading) {
    return <StatusState title={t("Loading sessions")} loading message={t("Fetching session list")} />;
  }

  if (sessionsQuery.error && !sessionsQuery.data) {
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
    <AdminTablePage>
    <Stack spacing={3}>
      <AdminPageHeader title={t("Sessions")} description={t("Create, activate, and open live draw views.")} refresh={{ isFetching: sessionsQuery.isFetching, failed: sessionsQuery.isRefetchError, error: sessionsQuery.error, onRefresh: () => sessionsQuery.refetch() }} actions={
        <Button component={RouterLink} to={`/sessions/new${location.search}`} variant="contained">
          {t("Create session")}</Button>
      } />

      <Stack spacing={1.5}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
            <TextField
              label={t("Search sessions")}
              placeholder={t("Name, room, or ID")}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                updateListState({ page: "" });
              }}
              size="small"
              sx={{ flex: 2, minWidth: { md: 260 } }}
            />
            <TextField
              select
              label={t("Status")}
              value={stateFilter}
              onChange={(event) => {
                updateListState({ status: event.target.value === "ALL" ? "" : event.target.value, page: "" });
              }}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">{t("All statuses")}</MenuItem>
              {sessionStates.map((state) => (
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
                updateListState({ room: event.target.value === "ALL" ? "" : event.target.value, page: "" });
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
                updateListState({ start: event.target.value === "ALL" ? "" : event.target.value, page: "" });
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
                <TableCell align="right">{t("Actions")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sessions.map((session) => (
                <TableRow key={session.id} hover>
                  <TableCell component="th" scope="row">
                    <Link component={RouterLink} to={`/sessions/${session.id}${location.search}`} fontWeight={600}>
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
                      {availableTransitions(session).map((nextState) => (
                        <Button
                          key={nextState}
                          size="small"
                          variant="outlined"
                          onClick={() => {
                            setTransitionError(null);
                            setPendingTransition({ sessionId: session.id, nextState });
                            setConfirmOpen(true);
                          }}
                        >
                          {translateAdminStatus(nextState, t)}
                        </Button>
                      ))}
                      {availableTransitions(session).length === 0 ? (
                        <Typography variant="body2" color="text.secondary">
                          {t("—")}</Typography>
                      ) : null}
                      {session.ticketCount > 0 && (allowedStates[session.state] ?? []).includes("CANCELLED") ? (
                        <Typography variant="caption" color="text.secondary">
                          {t("Cancellation unavailable after tickets have been sold.")}
                        </Typography>
                      ) : null}
                    </Stack>
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                      <Button
                        component={RouterLink}
                        to={`/session-tickets?sessionId=${session.id}`}
                        size="small"
                        variant="outlined"
                      >
                        {t("View tickets")}
                      </Button>
                      <Button size="small" variant="text" onClick={() => navigate(`/sessions/${session.id}${location.search}`)}>
                        {t("Open live view")}
                      </Button>
                    </Stack>
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
          dataLoaded={!!sessionsQuery.data}
          count={sessionsQuery.data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, nextPage) => updateListState({ page: String(nextPage) })}
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => {
            updateListState({ size: event.target.value, page: "" });
          }}
          rowsPerPageOptions={[10, 20, 50]}
        />
      </Stack>

      <ConfirmDialog
        open={confirmOpen}
        title={t("Change session state")}
        description={t("Transition the session to {{status}}?", {
          status: translateAdminStatus(pendingTransition?.nextState ?? "", t),
        })}
        confirmLabel={t("Confirm")}
        cancelLabel={t("Back")}
        confirming={transitionMutation.isPending}
        error={transitionError ? t(transitionError) : undefined}
        onCancel={() => {
          setConfirmOpen(false);
          setTransitionError(null);
        }}
        onConfirm={() => pendingTransition && transitionMutation.mutate(pendingTransition)}
      />
    </Stack>
    </AdminTablePage>
  );
}
