import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
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
  InputAdornment,
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
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";

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
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const sessionsQuery = useQuery({ queryKey: ["sessions"], queryFn: () => listSessions(0, 50) });
  const roomsQuery = useQuery({ queryKey: ["rooms"], queryFn: listRooms });
  const [draft, setDraft] = useState<SessionDraft | null>(null);
  const [search, setSearch] = useState("");
  const [stateFilter, setStateFilter] = useState("ALL");
  const [roomFilter, setRoomFilter] = useState("ALL");
  const [startFilter, setStartFilter] = useState("ALL");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingTransition, setPendingTransition] = useState<{ sessionId: number; nextState: string } | null>(null);

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
  const filteredSessions = sessions.filter((session) => {
    const query = search.trim().toLocaleLowerCase();
    const matchesSearch =
      !query ||
      session.sessionName.toLocaleLowerCase().includes(query) ||
      session.roomName.toLocaleLowerCase().includes(query) ||
      String(session.id).includes(query);
    const matchesState = stateFilter === "ALL" || session.state === stateFilter;
    const matchesRoom = roomFilter === "ALL" || String(session.roomId) === roomFilter;
    const scheduledStart = new Date(session.scheduledStartAt).getTime();
    const now = Date.now();
    const matchesStart =
      startFilter === "ALL" ||
      (startFilter === "UPCOMING" && scheduledStart >= now) ||
      (startFilter === "PAST" && scheduledStart < now);
    return matchesSearch && matchesState && matchesRoom && matchesStart;
  });
  const title = useMemo(() => (draft ? "Create session" : ""), [draft]);

  if (sessionsQuery.isLoading) {
    return <StatusState title="Loading sessions" loading message="Fetching session list" />;
  }

  if (sessionsQuery.error) {
    return (
      <StatusState
        title="Unable to load sessions"
        message={(sessionsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => sessionsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">Sessions</Typography>
          <Typography color="text.secondary">Create, activate, and open live draw views.</Typography>
        </div>
        <Button variant="contained" onClick={() => setDraft({ ...initialDraft })}>
          New session
        </Button>
      </Box>

      {sessions.length === 0 ? <Alert severity="info">No sessions found.</Alert> : null}

      {sessions.length > 0 ? (
        <Stack spacing={1.5}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
            <TextField
              label="Search sessions"
              placeholder="Name, room, or ID"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              size="small"
              sx={{ flex: 2, minWidth: { md: 260 } }}
              InputProps={{
                startAdornment: <InputAdornment position="start">⌕</InputAdornment>,
              }}
            />
            <TextField
              select
              label="Status"
              value={stateFilter}
              onChange={(event) => setStateFilter(event.target.value)}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">All statuses</MenuItem>
              {["DRAFT", "OPEN", "STARTED", "DRAWING", "CLOSED", "SETTLED", "CANCELLED"].map((state) => (
                <MenuItem key={state} value={state}>
                  {state}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Room"
              value={roomFilter}
              onChange={(event) => setRoomFilter(event.target.value)}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">All rooms</MenuItem>
              {rooms.map((room) => (
                <MenuItem key={room.id} value={String(room.id)}>
                  {room.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Start date"
              value={startFilter}
              onChange={(event) => setStartFilter(event.target.value)}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">Any time</MenuItem>
              <MenuItem value="UPCOMING">Upcoming</MenuItem>
              <MenuItem value="PAST">Past</MenuItem>
            </TextField>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Showing {filteredSessions.length} of {sessions.length} sessions
          </Typography>
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" sx={{ minWidth: 1050 }} aria-label="Sessions">
            <TableHead>
              <TableRow>
                <TableCell>Session</TableCell>
                <TableCell>Room</TableCell>
                <TableCell>Ticket price</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Tickets</TableCell>
                <TableCell align="right">Draws</TableCell>
                <TableCell>Scheduled start</TableCell>
                <TableCell>State actions</TableCell>
                <TableCell align="right">View</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredSessions.map((session) => (
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
                    <Chip label={session.state} color="primary" size="small" />
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
                          {nextState}
                        </Button>
                      ))}
                      {(allowedStates[session.state] ?? []).length === 0 ? (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      ) : null}
                    </Stack>
                  </TableCell>
                  <TableCell align="right">
                    <Button size="small" variant="text" onClick={() => navigate(`/sessions/${session.id}`)}>
                      Open live view
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {filteredSessions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                    No sessions match your search and filters.
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        </Stack>
      ) : null}

      <Dialog open={Boolean(draft)} onClose={() => setDraft(null)} fullWidth maxWidth="sm">
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              select
              label="Room"
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
              label="Session name"
              value={draft?.sessionName ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, sessionName: event.target.value })}
              fullWidth
            />
            <TextField
              label="Scheduled start"
              type="datetime-local"
              value={draft?.scheduledStartAt ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, scheduledStartAt: event.target.value })}
              fullWidth
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              select
              label="Initial state"
              value={draft?.state ?? "OPEN"}
              onChange={(event) => draft && setDraft({ ...draft, state: event.target.value })}
              fullWidth
            >
              {["DRAFT", "OPEN", "STARTED", "DRAWING", "CLOSED", "SETTLED", "CANCELLED"].map((state) => (
                <MenuItem key={state} value={state}>
                  {state}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => draft && createMutation.mutate(draft)}
            disabled={createMutation.isPending}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        title="Change session state"
        description={`Transition the session to ${pendingTransition?.nextState}?`}
        confirmLabel="Confirm"
        cancelLabel="Back"
        confirming={transitionMutation.isPending}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => pendingTransition && transitionMutation.mutate(pendingTransition)}
      />
    </Stack>
  );
}
