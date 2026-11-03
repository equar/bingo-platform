import { useTranslation } from "react-i18next";
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
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus, translateAdminValue } from "../../i18n";

function formatMoney(value: string | number) {
  const [whole, fraction = ""] = String(value).split(".");
  return `${whole}.${(fraction + "00").slice(0, 2)}`;
}

export function AgentDetailPage() {
  const { t } = useTranslation();
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
    onError: (error) => setLifecycleError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
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
    onError: (error) => setLifecycleError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const pinMutation = useMutation({
    mutationFn: () => setAgentPin(agentQuery.data!.userId, newPin),
    onSuccess: () => {
      setNewPin("");
      setPinError(null);
      setPinMessage(t("Agent PIN updated. The PIN is not displayed again."));
    },
    onError: (error) => {
      setPinMessage(null);
      setPinError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again.");
    },
  });

  if (agentQuery.isLoading) {
    return <StatusState title={t("Loading agent")} loading message={t("Fetching agent profile")} />;
  }

  if (agentQuery.error || !agentQuery.data) {
    return (
      <StatusState
        title={t("Unable to load agent")}
        message={t(getUserFacingErrorMessage(agentQuery.error) ?? "This item is no longer available. Refresh and try again.")}
        actionLabel={t("Retry")}
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
            {agent.normalizedPhone} {t("·")}{" "}{agent.city ?? "n/a"} {t("·")}{" "}{agent.territory ?? "n/a"}
          </Typography>
        </div>
        <Stack direction="row" spacing={1}>
          {agent.status === "PENDING" ? (
            <Button variant="contained" onClick={() => setApproveConfirmOpen(true)}>{t("Approve application")}</Button>
          ) : null}
          {agent.status === "APPROVED" ? (
            <Button color="warning" variant="outlined" onClick={() => setSuspendConfirmOpen(true)}>{t("Suspend agent")}</Button>
          ) : null}
          <Button component={RouterLink} to="/agents" variant="outlined">{t("Back to agents")}</Button>
        </Stack>
      </Box>

      {lifecycleError ? <Alert severity="error" role="alert">{t(lifecycleError)}</Alert> : null}

      {agent.status === "APPROVED" ? (
        <Card sx={{ border: "1px solid", borderColor: "divider" }}>
          <CardContent>
            <Stack spacing={2}>
              <Typography variant="h6">{t("Agent PIN management")}</Typography>
              <Alert severity="warning">{t("Set a new PIN only through an approved support process. The PIN is sent directly to the server and is never shown in agent details.")}</Alert>
              <TextField
                label={t("New agent PIN")}
                type="password"
                value={newPin}
                onChange={(event) => setNewPin(event.target.value.replace(/\D/g, "").slice(0, 8))}
                inputProps={{ inputMode: "numeric", maxLength: 8, pattern: "[0-9]{4,8}", autoComplete: "new-password" }}
                helperText={t("Enter 4 to 8 digits.")}
              />
              <Box>
                <Button
                  variant="outlined"
                  onClick={() => pinMutation.mutate()}
                  disabled={pinMutation.isPending || !/^[0-9]{4,8}$/.test(newPin)}
                >
                  {pinMutation.isPending ? t("Updating…") : t("Set agent PIN")}
                </Button>
              </Box>
              {pinError ? <Alert severity="error" role="alert">{t(pinError)}</Alert> : null}
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
                <Typography variant="overline">{t("Status")}</Typography>
                <Typography variant="h5">{translateAdminStatus(agent.status, t)}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t("User #")}{agent.userId} {t("·")}{" "}{t("Agent #")}{agent.agentId}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline">{t("Tickets sold")}</Typography>
                <Typography variant="h5">{agent.ticketSalesCount}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t("Activity exposed by the admin summary")}</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="overline">{t("Float tx / top-ups / payouts")}</Typography>
                <Typography variant="h5">
                  {agent.floatTransactionCount} {t("/")}{" "}{agent.topUpRequestCount} {t("/")}{" "}{agent.payoutVerificationCount}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t("Current available float:")}{" "}{floatBalanceQuery.data
                    ? `${formatMoney(floatBalanceQuery.data.availableBalance)} ${floatBalanceQuery.data.currency}`
                    : floatBalanceQuery.isLoading ? t("Loading…") : t("Unavailable")}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {floatBalanceQuery.isError ? <Alert severity="error">{t("Unable to load agent float:")}{" "}{t(getUserFacingErrorMessage(floatBalanceQuery.error) ?? "Something went wrong. Please try again.")}</Alert> : null}
      {floatHistoryQuery.isError ? <Alert severity="error">{t("Unable to load agent float history:")}{" "}{t(getUserFacingErrorMessage(floatHistoryQuery.error) ?? "Something went wrong. Please try again.")}</Alert> : null}
      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={1}>
            <Typography variant="h6">{t("Float transaction history")}</Typography>
            <Typography variant="body2" color="text.secondary">
              {t("Available")}{" "}{floatBalanceQuery.data ? formatMoney(floatBalanceQuery.data.availableBalance) : "—"} {floatBalanceQuery.data?.currency ?? ""} {t("· Clearing {{amount}}", { amount: floatBalanceQuery.data ? formatMoney(floatBalanceQuery.data.clearingBalance) : "—" })} {floatBalanceQuery.data?.currency ?? ""}
            </Typography>
            {(floatHistoryQuery.data?.content ?? []).length === 0 ? (
              <Typography color="text.secondary">{t("No float transactions recorded.")}</Typography>
            ) : (floatHistoryQuery.data?.content ?? []).map((entry) => (
              <Typography key={entry.id} variant="body2">
                {translateAdminValue(entry.transactionType, t)} {t("·")}{" "}{formatMoney(entry.amount)} {t("·")}{" "}
                {formatMoney(entry.availableBefore)} {t("→")}{" "}{formatMoney(entry.availableAfter)} {t("·")}
                {entry.relatedEntityType ? translateAdminValue(entry.relatedEntityType, t) : t("Unknown")} {t("#")}{entry.relatedEntityId ?? "n/a"} {t("·")}{" "}
                {new Date(entry.createdAt).toLocaleString()}
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
                <Typography variant="h6">{t("Float top-up review")}</Typography>
                <Alert severity="warning">{t("Top-up approvals are blocked pending approved funding verification and accounting rules. Rejection is available here.")}</Alert>
                <TextField
                  label={t("Request id")}
                  value={requestId}
                  onChange={(event) => setRequestId(event.target.value)}
                  fullWidth
                />
                <TextField label={t("Decision")} value="Reject" fullWidth disabled />
                <TextField
                  label={t("Review note")}
                  value={reviewNote}
                  onChange={(event) => setReviewNote(event.target.value)}
                  inputProps={{ maxLength: 500 }}
                  fullWidth
                  multiline
                  minRows={3}
                />
                <Alert severity="info">
                  {t("Idempotency is not supported by the backend review endpoint, so retry deduplication cannot be enforced here.")}</Alert>
                <Button
                  variant="contained"
                  onClick={() => setReviewConfirmOpen(true)}
                  disabled={!requestId.trim() || !reviewNote.trim()}
                >
                  {t("Review top-up")}</Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">{t("Commission rules")}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t("Publish a new effective rule version. The contract does not expose history, so the UI cannot render prior versions.")}</Typography>
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label={t("Commission rate (bps)")}
                      type="number"
                      value={commissionRateBps}
                      onChange={(event) => setCommissionRateBps(event.target.value)}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label={t("Effective from")}
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
                      label={t("Active")}
                      value={active ? "true" : "false"}
                      onChange={(event) => setActive(event.target.value === "true")}
                      fullWidth
                    >
                      <MenuItem value="true">{t("Active")}</MenuItem>
                      <MenuItem value="false">{t("Inactive")}</MenuItem>
                    </TextField>
                  </Grid>
                </Grid>
                <Button
                  variant="outlined"
                  onClick={() => commissionMutation.mutate()}
                  disabled={commissionMutation.isPending}
                >
                  {t("Publish rule")}</Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={1.5}>
            <Typography variant="h6">{t("Agent profile")}</Typography>
            <Typography>{t("City:")}{" "}{agent.city ?? t("n/a")}</Typography>
            <Typography>{t("Territory:")}{" "}{agent.territory ?? t("n/a")}</Typography>
            <Typography>{t("ID document type:")}{" "}{agent.idDocumentType || t("n/a")}</Typography>
            <Typography>{t("ID document number:")}{" "}{agent.idDocumentNumber || t("n/a")}</Typography>
            <Typography>{t("Front document reference:")}{" "}{agent.idDocumentFrontReference ?? t("n/a")}</Typography>
            <Typography>{t("Back document reference:")}{" "}{agent.idDocumentBackReference ?? t("n/a")}</Typography>
            <Typography>{t("Created:")}{" "}{new Date(agent.createdAt).toLocaleString()}</Typography>
            <Typography>{t("Updated:")}{" "}{new Date(agent.updatedAt).toLocaleString()}</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label={t("Tickets {{count}}", { count: agent.ticketSalesCount })} size="small" />
              <Chip label={t("Float tx {{count}}", { count: agent.floatTransactionCount })} size="small" />
              <Chip label={t("Top-ups {{count}}", { count: agent.topUpRequestCount })} size="small" />
              <Chip label={t("Payouts {{count}}", { count: agent.payoutVerificationCount })} size="small" />
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={approveConfirmOpen}
        title={t("Approve agent application")}
        description={t("Approve {{name}}? This changes the pending account to active and enables agent access.", { name: agent.displayName })}
        confirmLabel={t("Approve")}
        cancelLabel={t("Cancel")}
        confirming={approveMutation.isPending}
        onCancel={() => setApproveConfirmOpen(false)}
        onConfirm={() => approveMutation.mutate()}
      />

      <ConfirmDialog
        open={suspendConfirmOpen}
        title={t("Suspend agent")}
        description={t("Suspend {{name}}? Their new login and existing token access will be blocked.", { name: agent.displayName })}
        confirmLabel={t("Suspend")}
        cancelLabel={t("Cancel")}
        confirming={suspendMutation.isPending}
        onCancel={() => setSuspendConfirmOpen(false)}
        onConfirm={() => suspendMutation.mutate()}
      />

      <ConfirmDialog
        open={reviewConfirmOpen}
        title={t("Review float top-up")}
        description={t("Reject request {{id}}?", { id: requestId })}
        confirmLabel={t("Confirm")}
        cancelLabel={t("Back")}
        confirming={reviewMutation.isPending}
        onCancel={() => setReviewConfirmOpen(false)}
        onConfirm={() => reviewMutation.mutate()}
      />
    </Stack>
  );
}
