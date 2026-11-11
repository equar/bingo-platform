import { useTranslation } from "react-i18next";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Link,
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
import { Link as RouterLink, useSearchParams } from "react-router-dom";
import { searchSessionTicketSessions, searchSessionTickets } from "../../api/admin";
import { useSession } from "../../api/session";
import { getUserFacingErrorMessage } from "../../api/types";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { ListRefreshButton, ListRefreshError } from "../../components/ListRefreshButton";
import { StatusState } from "../../components/StatusState";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { translateAdminStatus } from "../../i18n";

function sessionIdFromSearch(search: string) {
  const parsed = Number(search);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export function SessionTicketsPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const currentSessionId = searchParams.get("sessionId") ?? "";
  const [sessionQuery, setSessionQuery] = useState(currentSessionId);
  const [sessionPage, setSessionPage] = useListSessionState("session-tickets:session-page", 0);
  const [ticketQuery, setTicketQuery] = usePrivateListSearch("session-tickets-query");
  const [ticketPage, setTicketPage] = useListSessionState("session-tickets:page", 0);
  const [ticketPageSize, setTicketPageSize] = useListSessionState("session-tickets:size", 20);
  const selectedSessionId = sessionIdFromSearch(currentSessionId);
  const session = useSession();
  const canOpenPlayer = session?.user.roles.includes("ADMIN") ?? false;

  const sessionsQuery = useQuery({
    queryKey: ["session-ticket-lookup-sessions", sessionQuery.trim(), sessionPage],
    queryFn: () => searchSessionTicketSessions(sessionQuery.trim() || undefined, sessionPage, 20),
    placeholderData: keepPreviousData,
  });

  const ticketsQuery = useQuery({
    queryKey: ["session-tickets", selectedSessionId, ticketQuery.trim(), ticketPage, ticketPageSize],
    queryFn: () => {
      if (selectedSessionId === null) {
        throw new Error("Select a session before searching its tickets.");
      }
      return searchSessionTickets(
        selectedSessionId,
        ticketQuery.trim() || undefined,
        ticketPage,
        ticketPageSize,
      );
    },
    enabled: selectedSessionId !== null,
    placeholderData: keepPreviousData,
  });

  const sessions = sessionsQuery.data?.content ?? [];
  const selectedSession = sessions.find((item) => item.id === selectedSessionId);
  const tickets = ticketsQuery.data?.content ?? [];

  function searchSessions(value: string) {
    setSessionQuery(value);
    setSessionPage(0);
    setTicketPage(0);
    if (currentSessionId) {
      setSearchParams({}, { replace: true });
    }
  }

  function selectSession(sessionId: number) {
    setSessionQuery(String(sessionId));
    setSessionPage(0);
    setTicketQuery("");
    setTicketPage(0);
    setSearchParams({ sessionId: String(sessionId) }, { replace: true });
  }

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
    <Stack spacing={3}>
      <AdminPageHeader title={t("Session ticket lookup")} description={t("Find a session, then review the tickets purchased for it.")} refresh={{ isFetching: sessionsQuery.isFetching, failed: sessionsQuery.isRefetchError, error: sessionsQuery.error, onRefresh: () => sessionsQuery.refetch() }} />

      <Stack spacing={1.5}>
        <TextField
          label={t("Search sessions")}
          placeholder={t("Session name, room, or ID")}
          value={sessionQuery}
          onChange={(event) => searchSessions(event.target.value)}
          size="small"
          fullWidth
        />
        {sessionsQuery.isFetching ? <Alert severity="info">{t("Updating session results...")}</Alert> : null}
        <Typography variant="body2" color="text.secondary">
          {t("Showing")} {sessions.length} {t("of")} {sessionsQuery.data?.totalElements ?? 0} {t("sessions")}
        </Typography>
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label={t("Sessions")}>
            <TableHead>
              <TableRow>
                <TableCell>{t("Session")}</TableCell>
                <TableCell>{t("Room")}</TableCell>
                <TableCell>{t("Status")}</TableCell>
                <TableCell>{t("Scheduled start")}</TableCell>
                <TableCell align="right">{t("Tickets")}</TableCell>
                <TableCell align="right">{t("Action")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sessions.map((item) => (
                <TableRow key={item.id} hover selected={item.id === selectedSessionId}>
                  <TableCell>
                    <Typography fontWeight={600}>{item.sessionName}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {t("#")}{item.id}
                    </Typography>
                  </TableCell>
                  <TableCell>{item.roomName}</TableCell>
                  <TableCell>
                    <Chip label={translateAdminStatus(item.state, t)} size="small" />
                  </TableCell>
                  <TableCell>{new Date(item.scheduledStartAt).toLocaleString()}</TableCell>
                  <TableCell align="right">{item.ticketCount}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      variant={item.id === selectedSessionId ? "contained" : "outlined"}
                      onClick={() => selectSession(item.id)}
                    >
                      {t("View tickets")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {sessions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    {t("No sessions match your search.")}
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        <AdminTablePagination
          component="div"
          dataLoaded={!!sessionsQuery.data}
          count={sessionsQuery.data?.totalElements ?? 0}
          page={sessionPage}
          onPageChange={(_, nextPage) => setSessionPage(nextPage)}
          rowsPerPage={20}
          onRowsPerPageChange={() => undefined}
          rowsPerPageOptions={[20]}
        />
      </Stack>

      {selectedSessionId === null ? (
        <Alert severity="info">{t("Select a session to see its tickets.")}</Alert>
      ) : (
        <Stack spacing={1.5}>
          <Box>
            <Typography variant="h5">
              {selectedSession?.sessionName ?? t("Session")} {t("#")}{selectedSessionId}
            </Typography>
            <Typography color="text.secondary">
              {selectedSession ? `${selectedSession.roomName} · ${translateAdminStatus(selectedSession.state, t)}` : ""}
              {ticketsQuery.data ? ` · ${ticketsQuery.data.totalElements} ${t("tickets")}` : ""}
            </Typography>
          </Box>
          <TextField
            label={t("Search tickets")}
            placeholder={t("Player name, phone, public code, or ticket ID (#123)")}
            value={ticketQuery}
            onChange={(event) => {
              setTicketQuery(event.target.value);
              setTicketPage(0);
            }}
            size="small"
            fullWidth
          />
          <Stack direction="row" justifyContent="flex-end" alignItems="center">
            <ListRefreshButton refreshing={ticketsQuery.isFetching} onRefresh={() => ticketsQuery.refetch()} />
          </Stack>
          {ticketsQuery.isRefetchError ? <ListRefreshError retrying={ticketsQuery.isFetching} error={ticketsQuery.error} onRetry={() => ticketsQuery.refetch()} /> : null}
          {ticketsQuery.error && !ticketsQuery.data ? (
            <Alert severity="error">
              {t(getUserFacingErrorMessage(ticketsQuery.error) ?? "Unable to load session tickets.")}
            </Alert>
          ) : null}
          {ticketsQuery.isFetching ? <Alert severity="info">{t("Updating ticket results...")}</Alert> : null}
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" aria-label={t("Tickets for session {{sessionId}}", { sessionId: selectedSessionId })}>
              <TableHead>
                <TableRow>
                  <TableCell>{t("Ticket ID")}</TableCell>
                  <TableCell>{t("Player")}</TableCell>
                  <TableCell>{t("Phone")}</TableCell>
                  <TableCell>{t("Public code")}</TableCell>
                  <TableCell>{t("Purchased")}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {tickets.map((ticket) => (
                  <TableRow key={ticket.ticketId} hover>
                    <TableCell>{t("#")}{ticket.ticketId}</TableCell>
                    <TableCell>
                      {canOpenPlayer ? (
                        <Link component={RouterLink} to={`/players/${ticket.playerId}`} underline="hover">
                          {ticket.playerName}
                        </Link>
                      ) : ticket.playerName}
                    </TableCell>
                    <TableCell>{ticket.playerPhone ?? t("Not available")}</TableCell>
                    <TableCell>{ticket.publicTicketCode}</TableCell>
                    <TableCell>{new Date(ticket.purchasedAt).toLocaleString()}</TableCell>
                  </TableRow>
                ))}
                {!ticketsQuery.isFetching && !ticketsQuery.error && tickets.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      {ticketQuery.trim()
                        ? t("No tickets match this session and search.")
                        : t("No tickets have been purchased for this session.")}
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </TableContainer>
          <AdminTablePagination
            component="div"
            dataLoaded={!!ticketsQuery.data}
            count={ticketsQuery.data?.totalElements ?? 0}
            page={ticketPage}
            onPageChange={(_, nextPage) => setTicketPage(nextPage)}
            rowsPerPage={ticketPageSize}
            onRowsPerPageChange={(event) => {
              setTicketPageSize(Number(event.target.value));
              setTicketPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50]}
          />
        </Stack>
      )}
    </Stack>
  );
}
