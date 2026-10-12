import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
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

      <Grid container spacing={2}>
        {sessions.map((session) => (
          <Grid item xs={12} md={6} key={session.id}>
            <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardContent>
                <Stack spacing={1.5}>
                  <Box display="flex" justifyContent="space-between" gap={2} alignItems="start">
                    <div>
                      <Typography variant="h6">
                        <RouterLink to={`/sessions/${session.id}`}>{session.sessionName}</RouterLink>
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {session.roomName} · {session.currency} {session.ticketPrice}
                      </Typography>
                    </div>
                    <Chip label={session.state} color="primary" size="small" />
                  </Box>
                  <Typography variant="body2">
                    Tickets: {session.ticketCount} · Draws: {session.drawCount}
                  </Typography>
                  <Typography variant="body2">Start: {new Date(session.scheduledStartAt).toLocaleString()}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
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
                  </Stack>
                  <Button variant="text" onClick={() => navigate(`/sessions/${session.id}`)}>
                    Open live view
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

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
