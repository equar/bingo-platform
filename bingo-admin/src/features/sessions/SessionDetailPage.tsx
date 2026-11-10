import { useTranslation } from "react-i18next";
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
import { Link as RouterLink, useLocation, useParams } from "react-router-dom";
import {
  DrawEventResponse,
  GameSessionResponse,
  postDraw,
  sessionDetail,
  sessionDraws,
  updateSessionState,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { useSession } from "../../api/session";
import { StatusState } from "../../components/StatusState";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { translateAdminStatus, translateAdminValue } from "../../i18n";
import { connectLiveDrawStream } from "./liveDrawSocket";

const allowedStates: Record<string, string[]> = {
  DRAFT: ["OPEN", "CANCELLED"],
  OPEN: ["STARTED", "CANCELLED"],
  STARTED: ["DRAWING", "CLOSED"],
  DRAWING: ["CLOSED"],
  CLOSED: ["SETTLED"],
};

function availableTransitions(session: GameSessionResponse) {
  const transitions = allowedStates[session.state] ?? [];
  return session.ticketCount > 0 ? transitions.filter((state) => state !== "CANCELLED") : transitions;
}

function mergeDraws(existing: DrawEventResponse[], incoming: DrawEventResponse[]) {
  const map = new Map<number, DrawEventResponse>();
  [...existing, ...incoming].forEach((draw) => map.set(draw.sequenceNo, draw));
  return Array.from(map.values()).sort((left, right) => left.sequenceNo - right.sequenceNo);
}

export function SessionDetailPage() {
  const location = useLocation();
  const { t } = useTranslation();
  const { sessionId } = useParams();
  const id = Number(sessionId);
  const queryClient = useQueryClient();
  const authSession = useSession();
  const [draws, setDraws] = useState<DrawEventResponse[]>([]);
  const [lastTriggeredDraw, setLastTriggeredDraw] = useState<DrawEventResponse | null>(null);
  const [drawError, setDrawError] = useState<string | null>(null);
  const [pendingState, setPendingState] = useState<string | null>(null);
  const [stateError, setStateError] = useState<string | null>(null);
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
    onError: (error: Error) => setDrawError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const stateMutation = useMutation({
    mutationFn: (nextState: string) => updateSessionState(id, nextState),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["session", id] });
      await queryClient.invalidateQueries({ queryKey: ["sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setPendingState(null);
      setStateError(null);
    },
    onError: (error) => setStateError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const session: GameSessionResponse | undefined = sessionQuery.data;
  const sessionTransitions = useMemo(() => (session ? availableTransitions(session) : []), [session]);
  const roles = authSession?.user.roles ?? [];
  const canOperateDraws = roles.includes("ADMIN") &&
    !roles.some((role) => ["SUPPORT", "SUPPORT", "ADMIN"].includes(role));
  const sessionCanDraw = session?.state === "STARTED" || session?.state === "DRAWING";

  if (sessionQuery.isLoading) {
    return <StatusState title={t("Loading session")} loading message={t("Fetching live session details")} />;
  }

  if (sessionQuery.error || !session) {
    return (
      <StatusState
        title={t("Unable to load session")}
        message={t(getUserFacingErrorMessage(sessionQuery.error) ?? "This item is no longer available. Refresh and try again.")}
        actionLabel={t("Retry")}
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
            {session.roomName} {t("·")}{" "}{t("Session")}{" "}{session.id}
          </Typography>
        </div>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Chip label={translateAdminStatus(session.state, t)} color="primary" />
          <Button component={RouterLink} to={`/session-tickets?sessionId=${session.id}`} variant="outlined">
            {t("View tickets")}
          </Button>
          <Button component={RouterLink} to={`/sessions${location.search}`} variant="outlined">
            {t("Back to sessions")}</Button>
        </Stack>
      </Box>

      {drawError ? <Alert severity="warning">{t(drawError)}</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} lg={5}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">{t("Session details")}</Typography>
                <Typography>{t("Scheduled start:")}{" "}{new Date(session.scheduledStartAt).toLocaleString()}</Typography>
                <Typography>
                  {t("Started:")}{" "}{session.startedAt ? new Date(session.startedAt).toLocaleString() : "n/a"}
                </Typography>
                <Typography>{t("Ended:")}{" "}{session.endedAt ? new Date(session.endedAt).toLocaleString() : "n/a"}</Typography>
                <Typography>{t("Ticket count:")}{" "}{session.ticketCount}</Typography>
                <Typography>{t("Draw count:")}{" "}{session.drawCount}</Typography>
                <Typography>{t("Capacity:")}{" "}{session.sessionCapacity}</Typography>
                <Typography>{t("Max tickets/player:")}{" "}{session.maxTicketsPerPlayer}</Typography>
                <Typography>
                  {t("Ticket sales close on start:")}{" "}{session.ticketSalesCloseOnSessionStart ? "Yes" : "No"}
                </Typography>
                <Typography>
                  {t("Multiple winners per prize:")}{" "}{session.allowMultipleWinnersPerPrize ? "Yes" : "No"}
                </Typography>
                <Typography>
                  {t("Patterns:")}{" "}{session.configuredPatterns.map((pattern) => translateAdminValue(pattern, t)).join(", ")}
                </Typography>
                <Typography>{t("Prizes:")}{" "}{session.configuredPrizes.length}</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {sessionTransitions.map((nextState: string) => (
                    <Button
                      key={nextState}
                      data-testid={`session-transition-${nextState.toLowerCase()}`}
                      size="small"
                      variant="outlined"
                      disabled={stateMutation.isPending}
                      onClick={() => {
                        setStateError(null);
                        setPendingState(nextState);
                      }}
                    >
                      {translateAdminStatus(nextState, t)}
                    </Button>
                  ))}
                {session.ticketCount > 0 && (allowedStates[session.state] ?? []).includes("CANCELLED") ? (
                  <Typography variant="caption" color="text.secondary">
                    {t("Cancellation unavailable after tickets have been sold.")}
                  </Typography>
                ) : null}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={7}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">{t("Live draw")}</Typography>
                {canOperateDraws ? (
                    <Button
                      data-testid="automatic-draw-button"
                      variant="contained"
                      onClick={() => drawMutation.mutate()}
                      disabled={drawMutation.isPending || !sessionCanDraw}
                    >
                      {drawMutation.isPending ? t("Drawing…") : t("Draw next number")}
                    </Button>
                ) : null}
                {!sessionCanDraw ? (
                  <Typography color="text.secondary">{t("Start the session before drawing numbers.")}</Typography>
                ) : null}
                {lastTriggeredDraw ? (
                  <Alert severity="success" data-testid="automatic-draw-result">
                    {t("Server drew")}{" "}{lastTriggeredDraw.numberDrawn} {t("at sequence")}{" "}{lastTriggeredDraw.sequenceNo}{t(".")}</Alert>
                ) : null}
                <Alert severity="info">
                  {t("Numbers are selected by the server. WebSocket feed and REST replay are merged by sequence number.")}</Alert>
                <Stack spacing={1}>
                  {draws.length === 0 ? <Typography color="text.secondary">{t("No draws recorded yet.")}</Typography> : null}
                  {draws.map((draw: DrawEventResponse) => (
                    <Card key={draw.sequenceNo} variant="outlined">
                      <CardContent>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <div>
                            <Typography fontWeight={700}>
                              {t("#")}{draw.sequenceNo} {t("·")}{" "}{draw.numberDrawn}
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
      <ConfirmDialog
        open={Boolean(pendingState)}
        title={t("Change session state")}
        description={t("Transition the session to {{status}}?", {
          status: translateAdminStatus(pendingState ?? "", t),
        })}
        confirmLabel={t("Confirm")}
        cancelLabel={t("Back")}
        confirming={stateMutation.isPending}
        error={stateError ? t(stateError) : undefined}
        onCancel={() => {
          setPendingState(null);
          setStateError(null);
        }}
        onConfirm={() => pendingState && stateMutation.mutate(pendingState)}
      />
    </Stack>
  );
}
