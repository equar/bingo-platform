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
import { adminAgentDetail, approveAgent, commissionRule, reviewFloatTopUpRequest, suspendAgent } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

export function AgentDetailPage() {
  const { agentId } = useParams();
  const id = Number(agentId);
  const queryClient = useQueryClient();
  const idempotency = useIdempotencyKey();
  const [requestId, setRequestId] = useState("");
  const [reviewNote, setReviewNote] = useState("");
  const [approveTopUp, setApproveTopUp] = useState(true);
  const [reviewConfirmOpen, setReviewConfirmOpen] = useState(false);
  const [approveConfirmOpen, setApproveConfirmOpen] = useState(false);
  const [suspendConfirmOpen, setSuspendConfirmOpen] = useState(false);
  const [lifecycleError, setLifecycleError] = useState<string | null>(null);
  const [commissionRateBps, setCommissionRateBps] = useState("0");
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 16));
  const [active, setActive] = useState(true);

  const agentQuery = useQuery({
    queryKey: ["agent", id],
    queryFn: () => adminAgentDetail(id),
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
    mutationFn: () => reviewFloatTopUpRequest(Number(requestId), { approve: approveTopUp, reviewNote }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setRequestId("");
      setReviewNote("");
      setApproveTopUp(true);
      setReviewConfirmOpen(false);
      idempotency.resetKey();
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
                  No admin float ledger detail endpoint is exposed.
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Alert severity="warning">
        The backend only exposes summary counts for this agent. Float ledger, payout detail, and commission history
        endpoints are not available to the admin contract.
      </Alert>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Float top-up review</Typography>
                <Typography variant="body2" color="text.secondary">
                  Review float top-up requests by id. The contract does not expose a searchable queue, so this form
                  targets a specific request.
                </Typography>
                <TextField
                  label="Request id"
                  value={requestId}
                  onChange={(event) => setRequestId(event.target.value)}
                  fullWidth
                />
                <TextField
                  select
                  label="Decision"
                  value={approveTopUp ? "APPROVE" : "REJECT"}
                  onChange={(event) => setApproveTopUp(event.target.value === "APPROVE")}
                  fullWidth
                >
                  <MenuItem value="APPROVE">Approve</MenuItem>
                  <MenuItem value="REJECT">Reject</MenuItem>
                </TextField>
                <TextField
                  label="Review note"
                  value={reviewNote}
                  onChange={(event) => setReviewNote(event.target.value)}
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
        description={`Review request ${requestId} as ${approveTopUp ? "approved" : "rejected"}?`}
        confirmLabel="Confirm"
        cancelLabel="Back"
        confirming={reviewMutation.isPending}
        onCancel={() => setReviewConfirmOpen(false)}
        onConfirm={() => reviewMutation.mutate()}
      />
    </Stack>
  );
}
