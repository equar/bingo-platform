import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  DrawEventResponse,
  GameSessionResponse,
  postDraw,
  sessionDetail,
  sessionDraws,
  updateSessionState,
} from "../../api/admin";
import { useSession } from "../../api/session";
import { StatusState } from "../../components/StatusState";
import { connectLiveDrawStream } from "./liveDrawSocket";

const allowedStates: Record<string, string[]> = {
  DRAFT: ["OPEN", "CANCELLED"],
  OPEN: ["STARTED", "CANCELLED"],
  STARTED: ["DRAWING", "CLOSED", "CANCELLED"],
  DRAWING: ["CLOSED", "CANCELLED"],
  CLOSED: ["SETTLED", "CANCELLED"],
};

function mergeDraws(existing: DrawEventResponse[], incoming: DrawEventResponse[]) {
  const map = new Map<number, DrawEventResponse>();
  [...existing, ...incoming].forEach((draw) => map.set(draw.sequenceNo, draw));
  return Array.from(map.values()).sort((left, right) => left.sequenceNo - right.sequenceNo);
}

export function SessionDetailPage() {
  const { sessionId } = useParams();
  const id = Number(sessionId);
  const queryClient = useQueryClient();
  const authSession = useSession();
  const [draws, setDraws] = useState<DrawEventResponse[]>([]);
  const [lastTriggeredDraw, setLastTriggeredDraw] = useState<DrawEventResponse | null>(null);
  const [drawError, setDrawError] = useState<string | null>(null);
  const liveSequenceRef = useRef(0);

  const sessionQuery = useQuery({
    queryKey: ["session", id],
    queryFn: () => sessionDetail(id),
    enabled: Number.isFinite(id),
  });
  const historyQuery = useQuery({
    queryKey: ["session-draws", id],
    queryFn: () => sessionDraws(id),
    enabled: Number.isFinite(id),
  });

  useEffect(() => {
    if (historyQuery.data) {
      setDraws(historyQuery.data);
      liveSequenceRef.current = historyQuery.data.reduce(
        (maxSequence: number, draw: DrawEventResponse) => Math.max(maxSequence, draw.sequenceNo),
        0,
      );
    }
  }, [historyQuery.data]);

  useEffect(() => {
    if (!authSession?.accessToken || !Number.isFinite(id)) {
      return;
    }
    const stream = connectLiveDrawStream({
      accessToken: authSession.accessToken,
      sessionId: id,
      onDraw: (event: DrawEventResponse) => setDraws((current: DrawEventResponse[]) => mergeDraws(current, [event])),
      onError: (message) => setDrawError(message),
      onGap: async (fromSequence) => {
        const missing = await sessionDraws(id, fromSequence);
        setDraws((current: DrawEventResponse[]) => mergeDraws(current, missing));
        liveSequenceRef.current = Math.max(
          liveSequenceRef.current,
          ...missing.map((item: DrawEventResponse) => item.sequenceNo),
          fromSequence ?? 0,
        );
      },
    });
    return () => stream.close();
  }, [authSession?.accessToken, id]);

  const drawMutation = useMutation({
    mutationFn: () => postDraw(id),
    onSuccess: async (draw: DrawEventResponse) => {
      setLastTriggeredDraw(draw);
      setDraws((current: DrawEventResponse[]) => mergeDraws(current, [draw]));
      liveSequenceRef.current = Math.max(liveSequenceRef.current, draw.sequenceNo);
      setDrawError(null);
      await queryClient.invalidateQueries({ queryKey: ["session", id] });
      await queryClient.invalidateQueries({ queryKey: ["session-draws", id] });
    },
    onError: (error: Error) => setDrawError(error.message || "Automatic draw failed"),
  });

  const stateMutation = useMutation({
    mutationFn: (nextState: string) => updateSessionState(id, nextState),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["session", id] });
      await queryClient.invalidateQueries({ queryKey: ["sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  const session: GameSessionResponse | undefined = sessionQuery.data;
  const availableTransitions = useMemo(() => (session ? (allowedStates[session.state] ?? []) : []), [session]);
  const roles = authSession?.user.roles ?? [];
  const canOperateDraws = roles.includes("ADMIN") &&
    !roles.some((role) => ["SUPPORT", "FINANCE_VERIFIER", "FINANCE_APPROVER"].includes(role));
  const sessionCanDraw = session?.state === "STARTED" || session?.state === "DRAWING";

  if (sessionQuery.isLoading) {
    return <StatusState title="Loading session" loading message="Fetching live session details" />;
  }

  if (sessionQuery.error || !session) {
    return (
      <StatusState
        title="Unable to load session"
        message={(sessionQuery.error as Error | undefined)?.message ?? "Session not found"}
        actionLabel="Retry"
        onAction={() => sessionQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">{session.sessionName}</Typography>
          <Typography color="text.secondary">
            {session.roomName} · Session {session.id}
          </Typography>
        </div>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Chip label={session.state} color="primary" />
          <Button component={RouterLink} to="/sessions" variant="outlined">
            Back to sessions
          </Button>
        </Stack>
      </Box>

      {drawError ? <Alert severity="warning">{drawError}</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} lg={5}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Session details</Typography>
                <Typography>Scheduled start: {new Date(session.scheduledStartAt).toLocaleString()}</Typography>
                <Typography>
                  Started: {session.startedAt ? new Date(session.startedAt).toLocaleString() : "n/a"}
                </Typography>
                <Typography>Ended: {session.endedAt ? new Date(session.endedAt).toLocaleString() : "n/a"}</Typography>
                <Typography>Ticket count: {session.ticketCount}</Typography>
                <Typography>Draw count: {session.drawCount}</Typography>
                <Typography>Capacity: {session.sessionCapacity}</Typography>
                <Typography>Max tickets/player: {session.maxTicketsPerPlayer}</Typography>
                <Typography>
                  Ticket sales close on start: {session.ticketSalesCloseOnSessionStart ? "Yes" : "No"}
                </Typography>
                <Typography>
                  Multiple winners per prize: {session.allowMultipleWinnersPerPrize ? "Yes" : "No"}
                </Typography>
                <Typography>Patterns: {session.configuredPatterns.join(", ")}</Typography>
                <Typography>Prizes: {session.configuredPrizes.length}</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {availableTransitions.map((nextState: string) => (
                    <Button
                      key={nextState}
                      data-testid={`session-transition-${nextState.toLowerCase()}`}
                      size="small"
                      variant="outlined"
                      onClick={() => stateMutation.mutate(nextState)}
                    >
                      {nextState}
                    </Button>
                  ))}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={7}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Live draw</Typography>
                {canOperateDraws ? (
                    <Button
                      data-testid="automatic-draw-button"
                      variant="contained"
                      onClick={() => drawMutation.mutate()}
                      disabled={drawMutation.isPending || !sessionCanDraw}
                    >
                      {drawMutation.isPending ? "Drawing…" : "Draw next number"}
                    </Button>
                ) : null}
                {!sessionCanDraw ? (
                  <Typography color="text.secondary">Start the session before drawing numbers.</Typography>
                ) : null}
                {lastTriggeredDraw ? (
                  <Alert severity="success" data-testid="automatic-draw-result">
                    Server drew {lastTriggeredDraw.numberDrawn} at sequence {lastTriggeredDraw.sequenceNo}.
                  </Alert>
                ) : null}
                <Alert severity="info">
                  Numbers are selected by the server. WebSocket feed and REST replay are merged by sequence number.
                </Alert>
                <Stack spacing={1}>
                  {draws.length === 0 ? <Typography color="text.secondary">No draws recorded yet.</Typography> : null}
                  {draws.map((draw: DrawEventResponse) => (
                    <Card key={draw.sequenceNo} variant="outlined">
                      <CardContent>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <div>
                            <Typography fontWeight={700}>
                              #{draw.sequenceNo} · {draw.numberDrawn}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {new Date(draw.drawnAt).toLocaleString()}
                            </Typography>
                          </div>
                          <Chip label={draw.operationId} size="small" />
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
