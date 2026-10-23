import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useParams } from "react-router-dom";
import { adminAgentDetail, adminAgentFloatBalance, adminAgentFloatHistory, approveAgent, commissionRule, reviewFloatTopUpRequest, setAgentPin, suspendAgent } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";

function formatMoney(value: string | number) {
  const [whole, fraction = ""] = String(value).split(".");
  return `${whole}.${(fraction + "00").slice(0, 2)}`;
}

export function AgentDetailPage() {
  const { agentId } = useParams();
  const id = Number(agentId);
  const queryClient = useQueryClient();
  const [requestId, setRequestId] = useState("");
  const [reviewNote, setReviewNote] = useState("");
  const [reviewConfirmOpen, setReviewConfirmOpen] = useState(false);
  const [approveConfirmOpen, setApproveConfirmOpen] = useState(false);
  const [suspendConfirmOpen, setSuspendConfirmOpen] = useState(false);
  const [lifecycleError, setLifecycleError] = useState<string | null>(null);
  const [commissionRateBps, setCommissionRateBps] = useState("0");
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 16));
  const [active, setActive] = useState(true);
  const [newPin, setNewPin] = useState("");
  const [pinMessage, setPinMessage] = useState<string | null>(null);
  const [pinError, setPinError] = useState<string | null>(null);

  const agentQuery = useQuery({
    queryKey: ["agent", id],
    queryFn: () => adminAgentDetail(id),
    enabled: Number.isFinite(id),
  });
  const floatBalanceQuery = useQuery({
    queryKey: ["agent-float", id],
    queryFn: () => adminAgentFloatBalance(id),
    enabled: Number.isFinite(id),
  });
  const floatHistoryQuery = useQuery({
    queryKey: ["agent-float-history", id],
    queryFn: () => adminAgentFloatHistory(id),
    enabled: Number.isFinite(id),
  });

  const commissionMutation = useMutation({
    mutationFn: () =>
      commissionRule({
        commissionRateBps: Number(commissionRateBps),
        effectiveFrom: new Date(effectiveFrom).toISOString(),
        active,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setCommissionRateBps("0");
      setEffectiveFrom(new Date().toISOString().slice(0, 16));
      setActive(true);
    },
  });

  const reviewMutation = useMutation({
    mutationFn: () => reviewFloatTopUpRequest(Number(requestId), { approve: false, reviewNote }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["agents"] }),
        queryClient.invalidateQueries({ queryKey: ["agent-float", id] }),
        queryClient.invalidateQueries({ queryKey: ["agent-float-history", id] }),
        queryClient.invalidateQueries({ queryKey: ["admin-float-top-ups"] }),
      ]);
      setRequestId("");
      setReviewNote("");
      setReviewConfirmOpen(false);
    },
  });

  const approveMutation = useMutation({
    mutationFn: () => approveAgent(id),
    onSuccess: async () => {
      setApproveConfirmOpen(false);
      setLifecycleError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["agent", id] }),
        queryClient.invalidateQueries({ queryKey: ["agents"] }),
      ]);
    },
    onError: (error) => setLifecycleError((error as Error).message),
  });

  const suspendMutation = useMutation({
    mutationFn: () => suspendAgent(id),
    onSuccess: async () => {
      setSuspendConfirmOpen(false);
      setLifecycleError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["agent", id] }),
        queryClient.invalidateQueries({ queryKey: ["agents"] }),
      ]);
    },
    onError: (error) => setLifecycleError((error as Error).message),
  });

  const pinMutation = useMutation({
    mutationFn: () => setAgentPin(agentQuery.data!.userId, newPin),
    onSuccess: () => {
      setNewPin("");
      setPinError(null);
      setPinMessage("Agent PIN updated. The PIN is not displayed again.");
    },
    onError: (error) => {
      setPinMessage(null);
      setPinError((error as Error).message);
    },
  });

  if (agentQuery.isLoading) {
    return <StatusState title="Loading agent" loading message="Fetching agent profile" />;
  }

  if (agentQuery.error || !agentQuery.data) {
    return (
      <StatusState
        title="Unable to load agent"
        message={(agentQuery.error as Error | undefined)?.message ?? "Agent not found"}
        actionLabel="Retry"
        onAction={() => agentQuery.refetch()}
      />
    );
  }

  const agent = agentQuery.data;

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">{agent.displayName}</Typography>
          <Typography color="text.secondary">
            {agent.normalizedPhone} · {agent.city ?? "n/a"} · {agent.territory ?? "n/a"}
          </Typography>
        </div>
        <Stack direction="row" spacing={1}>
          {agent.status === "PENDING" ? (
            <Button variant="contained" onClick={() => setApproveConfirmOpen(true)}>Approve application</Button>
          ) : null}
          {agent.status === "APPROVED" ? (
            <Button color="warning" variant="outlined" onClick={() => setSuspendConfirmOpen(true)}>Suspend agent</Button>
          ) : null}
          <Button component={RouterLink} to="/agents" variant="outlined">Back to agents</Button>
        </Stack>
      </Box>

      {lifecycleError ? <Alert severity="error" role="alert">{lifecycleError}</Alert> : null}

      {agent.status === "APPROVED" ? (
        <Card sx={{ border: "1px solid", borderColor: "divider" }}>
          <CardContent>
            <Stack spacing={2}>
              <Typography variant="h6">Agent PIN management</Typography>
              <Alert severity="warning">Set a new PIN only through an approved support process. The PIN is sent directly to the server and is never shown in agent details.</Alert>
              <TextField
                label="New agent PIN"
                type="password"
                value={newPin}
                onChange={(event) => setNewPin(event.target.value.replace(/\D/g, "").slice(0, 8))}
                inputProps={{ inputMode: "numeric", maxLength: 8, pattern: "[0-9]{4,8}", autoComplete: "new-password" }}
                helperText="Enter 4 to 8 digits."
              />
              <Box>
                <Button
                  variant="outlined"
                  onClick={() => pinMutation.mutate()}
                  disabled={pinMutation.isPending || !/^[0-9]{4,8}$/.test(newPin)}
                >
                  {pinMutation.isPending ? "Updating…" : "Set agent PIN"}
                </Button>
              </Box>
              {pinError ? <Alert severity="error" role="alert">{pinError}</Alert> : null}
              {pinMessage ? <Alert severity="success" role="status">{pinMessage}</Alert> : null}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline">Status</Typography>
                <Typography variant="h5">{agent.status}</Typography>
                <Typography variant="body2" color="text.secondary">
                  User #{agent.userId} · Agent #{agent.agentId}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline">Tickets sold</Typography>
                <Typography variant="h5">{agent.ticketSalesCount}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Activity exposed by the admin summary
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline">Float tx / top-ups / payouts</Typography>
                <Typography variant="h5">
                  {agent.floatTransactionCount} / {agent.topUpRequestCount} / {agent.payoutVerificationCount}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Current available float: {floatBalanceQuery.data
                    ? `${formatMoney(floatBalanceQuery.data.availableBalance)} ${floatBalanceQuery.data.currency}`
                    : floatBalanceQuery.isLoading ? "Loading…" : "Unavailable"}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {floatBalanceQuery.isError ? <Alert severity="error">Unable to load agent float: {floatBalanceQuery.error.message}</Alert> : null}
      {floatHistoryQuery.isError ? <Alert severity="error">Unable to load agent float history: {floatHistoryQuery.error.message}</Alert> : null}
      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={1}>
            <Typography variant="h6">Float transaction history</Typography>
            <Typography variant="body2" color="text.secondary">
              Available {floatBalanceQuery.data ? formatMoney(floatBalanceQuery.data.availableBalance) : "—"} {floatBalanceQuery.data?.currency ?? ""} ·
              Clearing {floatBalanceQuery.data ? formatMoney(floatBalanceQuery.data.clearingBalance) : "—"} {floatBalanceQuery.data?.currency ?? ""}
            </Typography>
            {(floatHistoryQuery.data?.content ?? []).length === 0 ? (
              <Typography color="text.secondary">No float transactions recorded.</Typography>
            ) : (floatHistoryQuery.data?.content ?? []).map((entry) => (
              <Typography key={entry.id} variant="body2">
                {entry.transactionType} · {formatMoney(entry.amount)} · {formatMoney(entry.availableBefore)} → {formatMoney(entry.availableAfter)} ·
                {entry.relatedEntityType} #{entry.relatedEntityId ?? "n/a"} · {new Date(entry.createdAt).toLocaleString()}
              </Typography>
            ))}
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Float top-up review</Typography>
                <Alert severity="warning">Top-up approvals are blocked pending approved funding verification and accounting rules. Rejection is available here.</Alert>
                <TextField
                  label="Request id"
                  value={requestId}
                  onChange={(event) => setRequestId(event.target.value)}
                  fullWidth
                />
                <TextField label="Decision" value="Reject" fullWidth disabled />
                <TextField
                  label="Review note"
                  value={reviewNote}
                  onChange={(event) => setReviewNote(event.target.value)}
                  inputProps={{ maxLength: 500 }}
                  fullWidth
                  multiline
                  minRows={3}
                />
                <Alert severity="info">
                  Idempotency is not supported by the backend review endpoint, so retry deduplication cannot be enforced
                  here.
                </Alert>
                <Button
                  variant="contained"
                  onClick={() => setReviewConfirmOpen(true)}
                  disabled={!requestId.trim() || !reviewNote.trim()}
                >
                  Review top-up
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Commission rules</Typography>
                <Typography variant="body2" color="text.secondary">
                  Publish a new effective rule version. The contract does not expose history, so the UI cannot render
                  prior versions.
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Commission rate (bps)"
                      type="number"
                      value={commissionRateBps}
                      onChange={(event) => setCommissionRateBps(event.target.value)}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Effective from"
                      type="datetime-local"
                      value={effectiveFrom}
                      onChange={(event) => setEffectiveFrom(event.target.value)}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      select
                      label="Active"
                      value={active ? "true" : "false"}
                      onChange={(event) => setActive(event.target.value === "true")}
                      fullWidth
                    >
                      <MenuItem value="true">Active</MenuItem>
                      <MenuItem value="false">Inactive</MenuItem>
                    </TextField>
                  </Grid>
                </Grid>
                <Button
                  variant="outlined"
                  onClick={() => commissionMutation.mutate()}
                  disabled={commissionMutation.isPending}
                >
                  Publish rule
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={1.5}>
            <Typography variant="h6">Agent profile</Typography>
            <Typography>City: {agent.city ?? "n/a"}</Typography>
            <Typography>Territory: {agent.territory ?? "n/a"}</Typography>
            <Typography>ID document type: {agent.idDocumentType || "n/a"}</Typography>
            <Typography>ID document number: {agent.idDocumentNumber || "n/a"}</Typography>
            <Typography>Front document reference: {agent.idDocumentFrontReference ?? "n/a"}</Typography>
            <Typography>Back document reference: {agent.idDocumentBackReference ?? "n/a"}</Typography>
            <Typography>Created: {new Date(agent.createdAt).toLocaleString()}</Typography>
            <Typography>Updated: {new Date(agent.updatedAt).toLocaleString()}</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label={`Tickets ${agent.ticketSalesCount}`} size="small" />
              <Chip label={`Float tx ${agent.floatTransactionCount}`} size="small" />
              <Chip label={`Top-ups ${agent.topUpRequestCount}`} size="small" />
              <Chip label={`Payouts ${agent.payoutVerificationCount}`} size="small" />
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={approveConfirmOpen}
        title="Approve agent application"
        description={`Approve ${agent.displayName}? This changes the pending account to active and enables agent access.`}
        confirmLabel="Approve"
        cancelLabel="Cancel"
        confirming={approveMutation.isPending}
        onCancel={() => setApproveConfirmOpen(false)}
        onConfirm={() => approveMutation.mutate()}
      />

      <ConfirmDialog
        open={suspendConfirmOpen}
        title="Suspend agent"
        description={`Suspend ${agent.displayName}? Their new login and existing token access will be blocked.`}
        confirmLabel="Suspend"
        cancelLabel="Cancel"
        confirming={suspendMutation.isPending}
        onCancel={() => setSuspendConfirmOpen(false)}
        onConfirm={() => suspendMutation.mutate()}
      />

      <ConfirmDialog
        open={reviewConfirmOpen}
        title="Review float top-up"
        description={`Reject request ${requestId}?`}
        confirmLabel="Confirm"
        cancelLabel="Back"
        confirming={reviewMutation.isPending}
        onCancel={() => setReviewConfirmOpen(false)}
        onConfirm={() => reviewMutation.mutate()}
      />
    </Stack>
  );
}
