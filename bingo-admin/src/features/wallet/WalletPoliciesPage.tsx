import { useTranslation } from "react-i18next";
import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  FormControlLabel,
} from "@mui/material";
import { useLocation } from "react-router-dom";
import {
  currentSignupRewardSettings,
  currentWalletTransferSettings,
  saveSignupRewardSettings,
  saveWalletTransferSettings,
  signupRewardGrants,
  signupRewardSettingsHistory,
  walletTransferSettingsHistory,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { translateAdminStatus } from "../../i18n";

function toLocalInputValue(isoString: string | null | undefined) {
  if (!isoString) return "";
  const date = new Date(isoString);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toIsoValue(localValue: string) {
  return localValue ? new Date(localValue).toISOString() : "";
}

function getMinimumEffectiveDateTime() {
  const date = new Date();
  date.setSeconds(0, 0);
  date.setMinutes(date.getMinutes() + 1);
  return toLocalInputValue(date.toISOString());
}

function getMinimumEffectiveUntil(effectiveFrom: string) {
  const minimumFromNow = getMinimumEffectiveDateTime();
  if (!effectiveFrom) return minimumFromNow;

  const afterEffectiveFrom = new Date(effectiveFrom);
  if (Number.isNaN(afterEffectiveFrom.getTime())) return minimumFromNow;
  afterEffectiveFrom.setMinutes(afterEffectiveFrom.getMinutes() + 1);
  const minimumAfterFrom = toLocalInputValue(afterEffectiveFrom.toISOString());
  return minimumAfterFrom > minimumFromNow ? minimumAfterFrom : minimumFromNow;
}

function isValidPolicyDateRange(effectiveFrom: string, effectiveUntil: string) {
  if (!effectiveFrom || effectiveFrom < getMinimumEffectiveDateTime()) return false;
  return !effectiveUntil || effectiveUntil >= getMinimumEffectiveUntil(effectiveFrom);
}

function getPolicyStatus(
  enabled: boolean,
  activeNow: boolean,
  effectiveFrom: string,
  effectiveUntil: string | null,
) {
  if (!enabled) return "DISABLED";
  if (activeNow) return "ACTIVE";
  const now = Date.now();
  if (new Date(effectiveFrom).getTime() > now) return "SCHEDULED";
  if (effectiveUntil && new Date(effectiveUntil).getTime() <= now) return "EXPIRED";
  return "INACTIVE";
}

function getPolicyStatusColor(status: string): "success" | "info" | "warning" | "default" {
  if (status === "ACTIVE") return "success";
  if (status === "SCHEDULED") return "info";
  if (status === "EXPIRED") return "warning";
  return "default";
}

export function WalletPoliciesPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const location = useLocation();
  const isTransferPolicy = location.pathname === "/wallet/transfer-policy";
  const [formOpen, setFormOpen] = useState(false);
  const [historyPage, setHistoryPage] = useState(0);
  useEffect(() => {
    setHistoryPage(0);
    setFormOpen(false);
  }, [isTransferPolicy]);
  const rewardQuery = useQuery({
    queryKey: ["wallet-policies", "signup-reward"],
    queryFn: currentSignupRewardSettings,
    enabled: !isTransferPolicy,
  });
  const transferQuery = useQuery({
    queryKey: ["wallet-policies", "transfer"],
    queryFn: currentWalletTransferSettings,
    enabled: isTransferPolicy,
  });
  const rewardHistoryQuery = useQuery({
    queryKey: ["wallet-policies", "signup-reward-history", historyPage],
    queryFn: () => signupRewardSettingsHistory(historyPage, 20),
    enabled: !isTransferPolicy,
  });
  const transferHistoryQuery = useQuery({
    queryKey: ["wallet-policies", "transfer-history", historyPage],
    queryFn: () => walletTransferSettingsHistory(historyPage, 20),
    enabled: isTransferPolicy,
  });
  const grantsQuery = useQuery({
    queryKey: ["wallet-policies", "grants"],
    queryFn: () => signupRewardGrants(0, 20),
    enabled: !isTransferPolicy,
  });

  const [rewardDraft, setRewardDraft] = useState({
    enabled: false,
    rewardAmount: "0",
    currency: "ETB",
    effectiveFrom: "",
    effectiveUntil: "",
    reason: "",
  });
  const [transferDraft, setTransferDraft] = useState({
    enabled: false,
    maxSingleTransferAmount: "0",
    dailyOutgoingLimitAmount: "0",
    currency: "ETB",
    effectiveFrom: "",
    effectiveUntil: "",
    reason: "",
  });

  useEffect(() => {
    if (rewardQuery.data) {
      const defaultEffectiveFrom = getMinimumEffectiveDateTime();
      const savedEffectiveFrom = toLocalInputValue(rewardQuery.data.effectiveFrom);
      const effectiveFrom = savedEffectiveFrom >= defaultEffectiveFrom
        ? savedEffectiveFrom
        : defaultEffectiveFrom;
      const savedEffectiveUntil = toLocalInputValue(rewardQuery.data.effectiveUntil);
      setRewardDraft({
        enabled: rewardQuery.data.enabled,
        rewardAmount: rewardQuery.data.rewardAmount,
        currency: rewardQuery.data.currency,
        effectiveFrom,
        effectiveUntil: savedEffectiveUntil
          && savedEffectiveUntil >= getMinimumEffectiveUntil(effectiveFrom)
          ? savedEffectiveUntil
          : "",
        reason: rewardQuery.data.reason ?? "",
      });
    }
  }, [rewardQuery.data]);

  useEffect(() => {
    if (transferQuery.data) {
      const defaultEffectiveFrom = getMinimumEffectiveDateTime();
      const savedEffectiveFrom = toLocalInputValue(transferQuery.data.effectiveFrom);
      const effectiveFrom = savedEffectiveFrom >= defaultEffectiveFrom
        ? savedEffectiveFrom
        : defaultEffectiveFrom;
      const savedEffectiveUntil = toLocalInputValue(transferQuery.data.effectiveUntil);
      setTransferDraft({
        enabled: transferQuery.data.enabled,
        maxSingleTransferAmount: transferQuery.data.maxSingleTransferAmount,
        dailyOutgoingLimitAmount: transferQuery.data.dailyOutgoingLimitAmount,
        currency: transferQuery.data.currency,
        effectiveFrom,
        effectiveUntil: savedEffectiveUntil && savedEffectiveUntil >= getMinimumEffectiveUntil(effectiveFrom)
          ? savedEffectiveUntil
          : "",
        reason: transferQuery.data.reason ?? "",
      });
    }
  }, [transferQuery.data]);

  const saveRewardMutation = useMutation({
    mutationFn: async () =>
      saveSignupRewardSettings({
        enabled: rewardDraft.enabled,
        rewardAmount: Number(rewardDraft.rewardAmount),
        currency: rewardDraft.currency,
        effectiveFrom: toIsoValue(rewardDraft.effectiveFrom),
        effectiveUntil: rewardDraft.effectiveUntil ? toIsoValue(rewardDraft.effectiveUntil) : null,
        reason: rewardDraft.reason || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-policies"] });
      setFormOpen(false);
    },
  });

  const saveTransferMutation = useMutation({
    mutationFn: async () =>
      saveWalletTransferSettings({
        enabled: transferDraft.enabled,
        maxSingleTransferAmount: Number(transferDraft.maxSingleTransferAmount),
        dailyOutgoingLimitAmount: Number(transferDraft.dailyOutgoingLimitAmount),
        currency: transferDraft.currency,
        effectiveFrom: toIsoValue(transferDraft.effectiveFrom),
        effectiveUntil: transferDraft.effectiveUntil ? toIsoValue(transferDraft.effectiveUntil) : null,
        reason: transferDraft.reason || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-policies"] });
      setFormOpen(false);
    },
  });

  const rewardHistory = rewardHistoryQuery.data?.content ?? [];
  const transferHistory = transferHistoryQuery.data?.content ?? [];
  const grants = grantsQuery.data?.content ?? [];

  const activePolicyQuery = isTransferPolicy ? transferQuery : rewardQuery;

  if (activePolicyQuery.isLoading) {
    return (
      <StatusState
        title={isTransferPolicy ? t("Loading transfer policy") : t("Loading signup reward")}
        loading
        message={t("Fetching wallet policy settings")}
      />
      );
  }

  if (activePolicyQuery.error) {
    return (
      <StatusState
        title={isTransferPolicy ? t("Unable to load transfer policy") : t("Unable to load signup reward")}
        message={t(getUserFacingErrorMessage(activePolicyQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => activePolicyQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <AdminPageHeader
        title={isTransferPolicy ? t("Transfer policy") : t("Signup reward")}
        description={isTransferPolicy
          ? t("Controls the player-to-player transfer limit and daily outgoing cap.")
          : t("Grants promotional balance to new players once.")}
        actions={(
          <Button
            variant="contained"
            onClick={() => {
              if (isTransferPolicy) saveTransferMutation.reset();
              else saveRewardMutation.reset();
              setFormOpen(true);
            }}
          >
            {isTransferPolicy ? t("Add transfer policy") : t("Add signup reward")}
          </Button>
        )}
      />

      <Grid container spacing={2}>
        {!isTransferPolicy && formOpen ? <Grid item xs={12}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={rewardDraft.enabled}
                      onChange={(event) => setRewardDraft({ ...rewardDraft, enabled: event.target.checked })}
                    />
                  }
                  label={rewardDraft.enabled ? t("Enabled") : t("Disabled")}
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Reward amount")}
                      type="number"
                      value={rewardDraft.rewardAmount}
                      onChange={(event) => setRewardDraft({ ...rewardDraft, rewardAmount: event.target.value })}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Currency")}
                      value={rewardDraft.currency}
                      onChange={(event) =>
                        setRewardDraft({ ...rewardDraft, currency: event.target.value.toUpperCase() })
                      }
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Effective from")}
                      type="datetime-local"
                      value={rewardDraft.effectiveFrom}
                      onChange={(event) => setRewardDraft({ ...rewardDraft, effectiveFrom: event.target.value })}
                      inputProps={{ min: getMinimumEffectiveDateTime() }}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Effective until")}
                      type="datetime-local"
                      value={rewardDraft.effectiveUntil}
                      onChange={(event) => setRewardDraft({ ...rewardDraft, effectiveUntil: event.target.value })}
                      inputProps={{ min: getMinimumEffectiveUntil(rewardDraft.effectiveFrom) }}
                      helperText={t("Leave blank for no expiry. If set, the end time must be in the future.")}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField
                      label={t("Reason")}
                      value={rewardDraft.reason}
                      onChange={(event) => setRewardDraft({ ...rewardDraft, reason: event.target.value })}
                      fullWidth
                      multiline
                      minRows={2}
                    />
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    onClick={() => saveRewardMutation.mutate()}
                    disabled={
                      saveRewardMutation.isPending
                      || !isValidPolicyDateRange(rewardDraft.effectiveFrom, rewardDraft.effectiveUntil)
                    }
                  >
                    {t("Save reward policy")}
                  </Button>
                  <Button onClick={() => setFormOpen(false)} disabled={saveRewardMutation.isPending}>{t("Cancel")}</Button>
                </Stack>
                {saveRewardMutation.isError ? (
                  <Alert severity="error" role="alert">{t(getUserFacingErrorMessage(saveRewardMutation.error) ?? "Something went wrong. Please try again.")}</Alert>
                ) : null}
                <Alert severity={rewardQuery.data?.activeNow ? "success" : "info"}>
                  {rewardQuery.data?.activeNow
                    ? t("A reward policy is active now.")
                    : t("No active reward policy is currently in effect.")}
                </Alert>
              </Stack>
            </CardContent>
          </Card>
        </Grid> : null}

        {isTransferPolicy && formOpen ? <Grid item xs={12}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={transferDraft.enabled}
                      onChange={(event) => setTransferDraft({ ...transferDraft, enabled: event.target.checked })}
                    />
                  }
                  label={transferDraft.enabled ? t("Enabled") : t("Disabled")}
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Max single transfer")}
                      type="number"
                      value={transferDraft.maxSingleTransferAmount}
                      onChange={(event) =>
                        setTransferDraft({ ...transferDraft, maxSingleTransferAmount: event.target.value })
                      }
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Daily outgoing limit")}
                      type="number"
                      value={transferDraft.dailyOutgoingLimitAmount}
                      onChange={(event) =>
                        setTransferDraft({ ...transferDraft, dailyOutgoingLimitAmount: event.target.value })
                      }
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Currency")}
                      value={transferDraft.currency}
                      onChange={(event) =>
                        setTransferDraft({ ...transferDraft, currency: event.target.value.toUpperCase() })
                      }
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Effective from")}
                      type="datetime-local"
                      value={transferDraft.effectiveFrom}
                      onChange={(event) => setTransferDraft({ ...transferDraft, effectiveFrom: event.target.value })}
                      inputProps={{ min: getMinimumEffectiveDateTime() }}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label={t("Effective until")}
                      type="datetime-local"
                      value={transferDraft.effectiveUntil}
                      onChange={(event) => setTransferDraft({ ...transferDraft, effectiveUntil: event.target.value })}
                      inputProps={{ min: getMinimumEffectiveUntil(transferDraft.effectiveFrom) }}
                      helperText={t("Leave blank for no expiry. If set, the end time must be in the future.")}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField
                      label={t("Reason")}
                      value={transferDraft.reason}
                      onChange={(event) => setTransferDraft({ ...transferDraft, reason: event.target.value })}
                      fullWidth
                      multiline
                      minRows={2}
                    />
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    onClick={() => saveTransferMutation.mutate()}
                    disabled={saveTransferMutation.isPending || !isValidPolicyDateRange(transferDraft.effectiveFrom, transferDraft.effectiveUntil)}
                  >
                    {t("Save transfer policy")}
                  </Button>
                  <Button onClick={() => setFormOpen(false)} disabled={saveTransferMutation.isPending}>{t("Cancel")}</Button>
                </Stack>
                {saveTransferMutation.isError ? (
                  <Alert severity="error" role="alert">{t(getUserFacingErrorMessage(saveTransferMutation.error) ?? "Something went wrong. Please try again.")}</Alert>
                ) : null}
                <Alert severity={transferQuery.data?.activeNow ? "success" : "info"}>
                  {transferQuery.data?.activeNow
                    ? t("A transfer policy is active now.")
                    : t("No active transfer policy is currently in effect.")}
                </Alert>
              </Stack>
            </CardContent>
          </Card>
        </Grid> : null}
      </Grid>

      {isTransferPolicy ? (
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>{t("Transfer policy history")}</Typography>
                {transferHistoryQuery.error ? (
                  <Alert severity="error">{t(getUserFacingErrorMessage(transferHistoryQuery.error) ?? "Something went wrong. Please try again.")}</Alert>
                ) : null}
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small" aria-label={t("Transfer policy history")}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell align="right">{t("Max single transfer")}</TableCell>
                        <TableCell align="right">{t("Daily outgoing limit")}</TableCell>
                        <TableCell>{t("Currency")}</TableCell>
                        <TableCell>{t("Effective from")}</TableCell>
                        <TableCell>{t("Effective until")}</TableCell>
                        <TableCell>{t("Reason")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {transferHistory.map((entry) => (
                        <TableRow key={entry.id} hover>
                          <TableCell><Chip
                            label={translateAdminStatus(getPolicyStatus(entry.enabled, entry.activeNow, entry.effectiveFrom, entry.effectiveUntil), t)}
                            size="small"
                            color={getPolicyStatusColor(getPolicyStatus(entry.enabled, entry.activeNow, entry.effectiveFrom, entry.effectiveUntil))}
                          /></TableCell>
                          <TableCell align="right">{entry.maxSingleTransferAmount}</TableCell>
                          <TableCell align="right">{entry.dailyOutgoingLimitAmount}</TableCell>
                          <TableCell>{entry.currency}</TableCell>
                          <TableCell>{new Date(entry.effectiveFrom).toLocaleString()}</TableCell>
                          <TableCell>{entry.effectiveUntil ? new Date(entry.effectiveUntil).toLocaleString() : t("No expiry")}</TableCell>
                          <TableCell>{entry.reason ?? t("n/a")}</TableCell>
                        </TableRow>
                      ))}
                      {!transferHistoryQuery.isLoading && !transferHistoryQuery.error && transferHistory.length === 0 ? <TableRow><TableCell colSpan={7}>{t("No transfer policy history yet.")}</TableCell></TableRow> : null}
                    </TableBody>
                  </Table>
                </Box>
                <AdminTablePagination
                  component="div"
                  count={transferHistoryQuery.data?.totalElements ?? 0}
                  page={historyPage}
                  onPageChange={(_, nextPage) => setHistoryPage(nextPage)}
                  rowsPerPage={20}
                  onRowsPerPageChange={() => undefined}
                  rowsPerPageOptions={[]}
                />
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : (
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>{t("Reward history")}</Typography>
                {rewardHistoryQuery.error ? (
                  <Alert severity="error">{t(getUserFacingErrorMessage(rewardHistoryQuery.error) ?? "Something went wrong. Please try again.")}</Alert>
                ) : null}
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small" aria-label={t("Reward history")}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell align="right">{t("Reward amount")}</TableCell>
                        <TableCell>{t("Currency")}</TableCell>
                        <TableCell>{t("Effective from")}</TableCell>
                        <TableCell>{t("Effective until")}</TableCell>
                        <TableCell>{t("Reason")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rewardHistory.map((entry) => (
                        <TableRow key={entry.id} hover>
                          <TableCell><Chip
                            label={translateAdminStatus(getPolicyStatus(entry.enabled, entry.activeNow, entry.effectiveFrom, entry.effectiveUntil), t)}
                            size="small"
                            color={getPolicyStatusColor(getPolicyStatus(entry.enabled, entry.activeNow, entry.effectiveFrom, entry.effectiveUntil))}
                          /></TableCell>
                          <TableCell align="right">{entry.rewardAmount}</TableCell>
                          <TableCell>{entry.currency}</TableCell>
                          <TableCell>{new Date(entry.effectiveFrom).toLocaleString()}</TableCell>
                          <TableCell>{entry.effectiveUntil ? new Date(entry.effectiveUntil).toLocaleString() : t("No expiry")}</TableCell>
                          <TableCell>{entry.reason ?? t("n/a")}</TableCell>
                        </TableRow>
                      ))}
                      {!rewardHistoryQuery.isLoading && !rewardHistoryQuery.error && rewardHistory.length === 0 ? <TableRow><TableCell colSpan={6}>{t("No signup reward history yet.")}</TableCell></TableRow> : null}
                    </TableBody>
                  </Table>
                </Box>
                <AdminTablePagination
                  component="div"
                  count={rewardHistoryQuery.data?.totalElements ?? 0}
                  page={historyPage}
                  onPageChange={(_, nextPage) => setHistoryPage(nextPage)}
                  rowsPerPage={20}
                  onRowsPerPageChange={() => undefined}
                  rowsPerPageOptions={[]}
                />
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12}>
            <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>{t("Recent grants")}</Typography>
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small" aria-label={t("Recent grants")}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Player")}</TableCell>
                        <TableCell>{t("Phone number")}</TableCell>
                        <TableCell align="right">{t("Reward amount")}</TableCell>
                        <TableCell>{t("Currency")}</TableCell>
                        <TableCell>{t("Awarded")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {grants.map((grant) => (
                        <TableRow key={grant.id} hover>
                          <TableCell>{grant.playerDisplayName}</TableCell>
                          <TableCell>{grant.playerPhoneNumber}</TableCell>
                          <TableCell align="right">{grant.rewardAmount}</TableCell>
                          <TableCell>{grant.currency}</TableCell>
                          <TableCell>{new Date(grant.awardedAt).toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                      {grants.length === 0 ? <TableRow><TableCell colSpan={5}>{t("No signup reward grants yet.")}</TableCell></TableRow> : null}
                    </TableBody>
                  </Table>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}
