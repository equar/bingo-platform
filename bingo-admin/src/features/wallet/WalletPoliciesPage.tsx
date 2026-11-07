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
  List,
  ListItem,
  ListItemText,
  Stack,
  Switch,
  Tab,
  Tabs,
  TextField,
  Typography,
  FormControlLabel,
} from "@mui/material";
import { Link as RouterLink, useLocation } from "react-router-dom";
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

export function WalletPoliciesPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const location = useLocation();
  const isTransferPolicy = location.pathname === "/wallet/transfer-policy";
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
    queryKey: ["wallet-policies", "signup-reward-history"],
    queryFn: () => signupRewardSettingsHistory(0, 8),
    enabled: !isTransferPolicy,
  });
  const transferHistoryQuery = useQuery({
    queryKey: ["wallet-policies", "transfer-history"],
    queryFn: () => walletTransferSettingsHistory(0, 8),
    enabled: isTransferPolicy,
  });
  const grantsQuery = useQuery({
    queryKey: ["wallet-policies", "grants"],
    queryFn: () => signupRewardGrants(0, 8),
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
      setRewardDraft({
        enabled: rewardQuery.data.enabled,
        rewardAmount: rewardQuery.data.rewardAmount,
        currency: rewardQuery.data.currency,
        effectiveFrom: toLocalInputValue(rewardQuery.data.effectiveFrom),
        effectiveUntil: toLocalInputValue(rewardQuery.data.effectiveUntil),
        reason: rewardQuery.data.reason ?? "",
      });
    }
  }, [rewardQuery.data]);

  useEffect(() => {
    if (transferQuery.data) {
      setTransferDraft({
        enabled: transferQuery.data.enabled,
        maxSingleTransferAmount: transferQuery.data.maxSingleTransferAmount,
        dailyOutgoingLimitAmount: transferQuery.data.dailyOutgoingLimitAmount,
        currency: transferQuery.data.currency,
        effectiveFrom: toLocalInputValue(transferQuery.data.effectiveFrom),
        effectiveUntil: toLocalInputValue(transferQuery.data.effectiveUntil),
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
      <AdminPageHeader title={t("Wallet policies")} description={t("Manage player incentives and wallet transfer limits.")} />

      <Tabs value={isTransferPolicy ? "transfer" : "signup-reward"} aria-label={t("Wallet policy sections")}>
        <Tab
          component={RouterLink}
          to="/wallet/signup-reward"
          value="signup-reward"
          label={t("Signup reward")}
        />
        <Tab
          component={RouterLink}
          to="/wallet/transfer-policy"
          value="transfer"
          label={t("Transfer policy")}
        />
      </Tabs>

      <Grid container spacing={2}>
        {!isTransferPolicy ? <Grid item xs={12}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">{t("Signup reward")}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t("Grants promotional balance to new players once.")}</Typography>
                </Box>
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
                <Button
                  variant="contained"
                  onClick={() => saveRewardMutation.mutate()}
                  disabled={saveRewardMutation.isPending}
                  sx={{ alignSelf: "flex-start" }}
                >
                  {t("Save reward policy")}</Button>
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

        {isTransferPolicy ? <Grid item xs={12}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">{t("Transfer policy")}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t("Controls the player-to-player transfer limit and daily outgoing cap.")}</Typography>
                </Box>
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
                <Button
                  variant="contained"
                  onClick={() => saveTransferMutation.mutate()}
                  disabled={saveTransferMutation.isPending}
                  sx={{ alignSelf: "flex-start" }}
                >
                  {t("Save transfer policy")}</Button>
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
                <Typography variant="h6" gutterBottom>
                  {t("Transfer policy history")}</Typography>
                <List dense>
                  {transferHistory.map((entry) => (
                    <ListItem key={entry.id} divider>
                      <ListItemText
                        primary={`${entry.maxSingleTransferAmount} / ${entry.dailyOutgoingLimitAmount} ${entry.currency}`}
                        secondary={`${t(entry.enabled ? "Enabled" : "Disabled")} · ${new Date(entry.createdAt).toLocaleString()}`}
                      />
                      <Chip label={translateAdminStatus(entry.activeNow ? "ACTIVE" : "INACTIVE", t)} size="small" />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : (
        <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {t("Reward history")}</Typography>
              <List dense>
                {rewardHistory.map((entry) => (
                  <ListItem key={entry.id} divider>
                    <ListItemText
                      primary={`${entry.rewardAmount} ${entry.currency}`}
                      secondary={`${t(entry.enabled ? "Enabled" : "Disabled")} · ${new Date(entry.createdAt).toLocaleString()}`}
                    />
                    <Chip label={translateAdminStatus(entry.activeNow ? "ACTIVE" : "INACTIVE", t)} size="small" />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={4}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {t("Recent grants")}</Typography>
              <List dense>
                {grants.map((grant) => (
                  <ListItem key={grant.id} divider>
                    <ListItemText
                      primary={`${grant.playerDisplayName} · ${grant.rewardAmount} ${grant.currency}`}
                      secondary={`${grant.playerPhoneNumber} · ${new Date(grant.awardedAt).toLocaleString()}`}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
        </Grid>
      )}
    </Stack>
  );
}
