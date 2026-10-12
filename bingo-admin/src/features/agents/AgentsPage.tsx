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
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
  AdminAgentSummaryResponse,
  AgentProfileResponse,
  approveAgent,
  adminAgents,
  commissionRule,
  reviewFloatTopUpRequest,
  suspendAgent,
} from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

type AgentDecisionMode = "APPROVE" | "SUSPEND";

interface AgentDecisionDraft {
  mode: AgentDecisionMode;
  reason: string;
  profile: {
    displayName: string;
    phoneNumber: string;
    status: string;
    city: string;
    territory: string;
    idDocumentType: string;
    idDocumentNumber: string;
    idDocumentFrontReference: string;
    idDocumentBackReference: string;
  };
}

const emptyDecisionDraft: AgentDecisionDraft = {
  mode: "APPROVE",
  reason: "",
  profile: {
    displayName: "",
    phoneNumber: "",
    status: "APPROVED",
    city: "",
    territory: "",
    idDocumentType: "",
    idDocumentNumber: "",
    idDocumentFrontReference: "",
    idDocumentBackReference: "",
  },
};

function formatNullable(value: string | null | undefined) {
  return value?.trim() ? value : "n/a";
}

export function AgentsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const agentsQuery = useQuery({ queryKey: ["agents"], queryFn: () => adminAgents(0, 100) });
  const [search, setSearch] = useState("");
  const [selectedAgent, setSelectedAgent] = useState<AdminAgentSummaryResponse | null>(null);
  const [decisionDraft, setDecisionDraft] = useState<AgentDecisionDraft | null>(null);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [suspendConfirmOpen, setSuspendConfirmOpen] = useState(false);
  const [floatRequestId, setFloatRequestId] = useState("");
  const [floatApprove, setFloatApprove] = useState(true);
  const [floatReviewNote, setFloatReviewNote] = useState("");
  const [commissionRateBps, setCommissionRateBps] = useState("0");
  const [commissionEffectiveFrom, setCommissionEffectiveFrom] = useState(new Date().toISOString().slice(0, 16));
  const [commissionActive, setCommissionActive] = useState(true);
  const idempotency = useIdempotencyKey();

  const approveMutation = useMutation({
    mutationFn: async (body: AgentProfileResponse) => {
      if (!selectedAgent) {
        throw new Error("Missing selected agent");
      }
      return approveAgent(selectedAgent.userId, body);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setDecisionDraft(null);
      setSelectedAgent(null);
      setDecisionError(null);
    },
    onError: (error) => setDecisionError((error as Error).message),
  });

  const suspendMutation = useMutation({
    mutationFn: async () => {
      if (!selectedAgent) {
        throw new Error("Missing selected agent");
      }
      return suspendAgent(selectedAgent.agentId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setSuspendConfirmOpen(false);
      setSelectedAgent(null);
      setDecisionError(null);
    },
    onError: (error) => setDecisionError((error as Error).message),
  });

  const floatReviewMutation = useMutation({
    mutationFn: async () => {
      if (!floatRequestId.trim()) {
        throw new Error("Enter a float top-up request id");
      }
      idempotency.getKey();
      return reviewFloatTopUpRequest(Number(floatRequestId), {
        approve: floatApprove,
        reviewNote: floatReviewNote,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setFloatRequestId("");
      setFloatApprove(true);
      setFloatReviewNote("");
      idempotency.resetKey();
    },
  });

  const commissionMutation = useMutation({
    mutationFn: () =>
      commissionRule({
        commissionRateBps: Number(commissionRateBps),
        effectiveFrom: new Date(commissionEffectiveFrom).toISOString(),
        active: commissionActive,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setCommissionRateBps("0");
      setCommissionEffectiveFrom(new Date().toISOString().slice(0, 16));
      setCommissionActive(true);
    },
  });

  const filteredAgents = useMemo(() => {
    const normalized = search.trim().toLowerCase();
    const items = agentsQuery.data?.content ?? [];
    if (!normalized) {
      return items;
    }
    return items.filter((agent) => {
      const haystack = [agent.displayName, agent.normalizedPhone, agent.city ?? "", agent.territory ?? "", agent.status]
        .join(" ")
        .toLowerCase();
      return haystack.includes(normalized);
    });
  }, [agentsQuery.data, search]);

  if (agentsQuery.isLoading) {
    return <StatusState title="Loading agents" loading message="Fetching agent roster" />;
  }

  if (agentsQuery.error) {
    return (
      <StatusState
        title="Unable to load agents"
        message={(agentsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => agentsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">Agents</Typography>
          <Typography color="text.secondary">
            Review agent profiles, queue float top-up requests, and publish commission rules.
          </Typography>
        </div>
        <Button
          variant="outlined"
          onClick={() => navigate(`/agents/${filteredAgents[0]?.agentId ?? 0}`)}
          disabled={filteredAgents.length === 0}
        >
          Open agent detail
        </Button>
      </Box>

      <Alert severity="warning">
        The backend contract exposes agent summary counts, but not an admin float balance or ledger endpoint. The float
        column below is marked as unavailable until that API exists.
      </Alert>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Commission rules</Typography>
                <Typography variant="body2" color="text.secondary">
                  Publish a new version with an effective date. The backend does not expose rule history, so only the
                  latest submission is visible here.
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
                      value={commissionEffectiveFrom}
                      onChange={(event) => setCommissionEffectiveFrom(event.target.value)}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={commissionActive}
                          onChange={(event) => setCommissionActive(event.target.checked)}
                        />
                      }
                      label="Active"
                    />
                  </Grid>
                </Grid>
                <Button
                  variant="contained"
                  onClick={() => commissionMutation.mutate()}
                  disabled={commissionMutation.isPending}
                >
                  Publish rule
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Float top-up review</Typography>
                <Typography variant="body2" color="text.secondary">
                  The backend exposes review-by-id but not a searchable queue, so enter the request id from another
                  queue or source.
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  A local retry key is retained until the review succeeds, but the backend review endpoint does not
                  accept an idempotency header.
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Request id"
                      value={floatRequestId}
                      onChange={(event) => setFloatRequestId(event.target.value)}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      select
                      label="Decision"
                      value={floatApprove ? "APPROVE" : "REJECT"}
                      onChange={(event) => setFloatApprove(event.target.value === "APPROVE")}
                      fullWidth
                    >
                      <MenuItem value="APPROVE">Approve</MenuItem>
                      <MenuItem value="REJECT">Reject</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Review note"
                      value={floatReviewNote}
                      onChange={(event) => setFloatReviewNote(event.target.value)}
                      fullWidth
                      multiline
                      minRows={2}
                    />
                  </Grid>
                </Grid>
                <Button
                  variant="outlined"
                  onClick={() => floatReviewMutation.mutate()}
                  disabled={floatReviewMutation.isPending || !floatReviewNote}
                >
                  Review request
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <TextField
        label="Search agents"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        fullWidth
        helperText="Search by name, phone, city, territory, or status"
      />

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent sx={{ p: 0 }}>
          <Table size="small" aria-label="agents table">
            <TableHead>
              <TableRow>
                <TableCell>Agent</TableCell>
                <TableCell>City</TableCell>
                <TableCell>Territory</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Float</TableCell>
                <TableCell align="right">Tickets</TableCell>
                <TableCell align="right">Float tx</TableCell>
                <TableCell align="right">Top-ups</TableCell>
                <TableCell align="right">Payouts</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredAgents.map((agent) => (
                <TableRow key={agent.agentId} hover>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography fontWeight={600}>{agent.displayName}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {agent.normalizedPhone}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{formatNullable(agent.city)}</TableCell>
                  <TableCell>{formatNullable(agent.territory)}</TableCell>
                  <TableCell>
                    <Chip label={agent.status} size="small" />
                  </TableCell>
                  <TableCell>
                    <Chip label="Not exposed" variant="outlined" size="small" />
                  </TableCell>
                  <TableCell align="right">{agent.ticketSalesCount}</TableCell>
                  <TableCell align="right">{agent.floatTransactionCount}</TableCell>
                  <TableCell align="right">{agent.topUpRequestCount}</TableCell>
                  <TableCell align="right">{agent.payoutVerificationCount}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Button size="small" component={RouterLink} to={`/agents/${agent.agentId}`}>
                        Detail
                      </Button>
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => {
                          setSelectedAgent(agent);
                          setDecisionDraft({
                            mode: "APPROVE",
                            reason: "",
                            profile: {
                              displayName: agent.displayName,
                              phoneNumber: agent.normalizedPhone,
                              status: "APPROVED",
                              city: agent.city ?? "",
                              territory: agent.territory ?? "",
                              idDocumentType: "",
                              idDocumentNumber: "",
                              idDocumentFrontReference: "",
                              idDocumentBackReference: "",
                            },
                          });
                        }}
                      >
                        Approve
                      </Button>
                      <Button
                        size="small"
                        color="warning"
                        variant="outlined"
                        onClick={() => {
                          setSelectedAgent(agent);
                          setSuspendConfirmOpen(true);
                        }}
                      >
                        Suspend
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
              {filteredAgents.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10}>
                    <Alert severity="info">No agents match the current search.</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog
        open={Boolean(decisionDraft?.mode === "APPROVE")}
        onClose={() => setDecisionDraft(null)}
        fullWidth
        maxWidth="md"
      >
        <DialogTitle>{decisionDraft?.mode === "APPROVE" ? "Approve agent onboarding" : "Suspend agent"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {decisionError ? <Alert severity="error">{decisionError}</Alert> : null}
            <Alert severity="info">
              The backend contract does not accept a reason parameter for approve/suspend. Reason is operator context
              only and will not be persisted by the API.
            </Alert>
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Display name"
                  value={decisionDraft?.profile.displayName ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, displayName: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Phone number"
                  value={decisionDraft?.profile.phoneNumber ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, phoneNumber: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="City"
                  value={decisionDraft?.profile.city ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, city: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Territory"
                  value={decisionDraft?.profile.territory ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, territory: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="ID document type"
                  value={decisionDraft?.profile.idDocumentType ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, idDocumentType: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="ID document number"
                  value={decisionDraft?.profile.idDocumentNumber ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, idDocumentNumber: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Front image reference"
                  value={decisionDraft?.profile.idDocumentFrontReference ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, idDocumentFrontReference: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Back image reference"
                  value={decisionDraft?.profile.idDocumentBackReference ?? ""}
                  onChange={(event) =>
                    decisionDraft &&
                    setDecisionDraft({
                      ...decisionDraft,
                      profile: { ...decisionDraft.profile, idDocumentBackReference: event.target.value },
                    })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Operator reason"
                  value={decisionDraft?.reason ?? ""}
                  onChange={(event) =>
                    decisionDraft && setDecisionDraft({ ...decisionDraft, reason: event.target.value })
                  }
                  fullWidth
                  multiline
                  minRows={3}
                  helperText="Not sent to the backend; the current contract does not accept a reason field."
                />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecisionDraft(null)} disabled={approveMutation.isPending}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={() => {
              if (!selectedAgent || !decisionDraft) {
                return;
              }
              approveMutation.mutate({
                agentId: selectedAgent.agentId,
                userId: selectedAgent.userId,
                phoneNumber: decisionDraft.profile.phoneNumber,
                displayName: decisionDraft.profile.displayName,
                status: decisionDraft.profile.status,
                city: decisionDraft.profile.city || null,
                territory: decisionDraft.profile.territory || null,
                idDocumentType: decisionDraft.profile.idDocumentType || null,
                idDocumentNumber: decisionDraft.profile.idDocumentNumber || null,
                idDocumentFrontReference: decisionDraft.profile.idDocumentFrontReference || null,
                idDocumentBackReference: decisionDraft.profile.idDocumentBackReference || null,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
              });
            }}
            disabled={approveMutation.isPending}
          >
            {decisionDraft?.mode === "APPROVE" ? "Approve" : "Proceed"}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={suspendConfirmOpen}
        title="Suspend agent"
        description={
          selectedAgent
            ? `Suspend ${selectedAgent.displayName}? The backend endpoint does not accept a suspension reason.`
            : "Suspend selected agent?"
        }
        confirmLabel="Suspend"
        cancelLabel="Back"
        confirming={suspendMutation.isPending}
        onCancel={() => setSuspendConfirmOpen(false)}
        onConfirm={() => suspendMutation.mutate()}
      />
    </Stack>
  );
}
