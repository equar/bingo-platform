import {
  formatEthiopianDate,
  formatEthiopianDateTime,
} from "../../utils/ethiopianDateTime";
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
  FormControl,
  InputLabel,
  MenuItem,
  Select,
} from "@mui/material";
import { useLocation, useNavigate } from "react-router-dom";
import {
  currentSignupRewardSettings,
  currentWalletTransferSettings,
  promotionalRewardCampaigns,
  createPromotionalRewardCampaign,
  setSignupRewardPolicyStatus,
  saveWalletTransferSettings,
  walletTransferSettingsHistory,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { translateAdminStatus } from "../../i18n";
import {
  campaignEndExclusiveIso,
  campaignStartIso,
  getCampaignDateValue as toLocalDateValue,
  isValidCampaignDateRange as isValidRewardDateRange,
  todayCampaignDateValue as getTodayDateValue,
} from "./campaignDates";

function toLocalInputValue(isoString: string | null | undefined) {
  if (!isoString) return "";
  const date = new Date(isoString);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toIsoValue(localValue: string) {
  return localValue ? new Date(localValue).toISOString() : "";
}

function isValidRewardAmount(value: string | number) {
  const amount = String(value).trim();
  return /^\d{1,17}(?:\.\d{1,2})?$/.test(amount) && Number(amount) >= 0.01;
}

function formatPolicyDate(value: string | null, inclusiveEnd = false) {
  const dateValue = toLocalDateValue(value, inclusiveEnd);
  return dateValue ? formatEthiopianDate(`${dateValue}T12:00:00Z`) : "";
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
  if (!effectiveFrom || effectiveFrom < getMinimumEffectiveDateTime())
    return false;
  return (
    !effectiveUntil || effectiveUntil >= getMinimumEffectiveUntil(effectiveFrom)
  );
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
  if (effectiveUntil && new Date(effectiveUntil).getTime() <= now)
    return "EXPIRED";
  return "INACTIVE";
}

function getPolicyStatusColor(
  status: string,
): "success" | "info" | "warning" | "default" {
  if (status === "ACTIVE") return "success";
  if (status === "SCHEDULED") return "info";
  if (status === "EXPIRED") return "warning";
  return "default";
}

export function WalletPoliciesPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const location = useLocation();
  const navigate = useNavigate();
  const isTransferPolicy = location.pathname.startsWith("/wallet/transfer-policy");
  const isCreatePage = location.pathname.endsWith("/new");
  const listPath = isTransferPolicy ? "/wallet/transfer-policy" : "/wallet/signup-reward";
  const [rewardSearch, setRewardSearch] = usePrivateListSearch(
    "signup-reward-history",
  );
  const [transferQueryText, setTransferQueryText] = usePrivateListSearch(
    "transfer-policy-history",
  );
  const [rewardStatus, setRewardStatus] = useListSessionState(
    "signup-reward-history:status",
    "",
  );
  const [transferStatus, setTransferStatus] = useListSessionState(
    "transfer-policy-history:status",
    "",
  );
  const [historyPage, setHistoryPage] = useListSessionState(
    "wallet-policy-history:page",
    0,
  );
  useEffect(() => {
    setHistoryPage(0);
  }, [isTransferPolicy, setHistoryPage]);
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
    queryKey: ["wallet-policies", "promotional-campaigns", historyPage],
    queryFn: () =>
      promotionalRewardCampaigns(
        historyPage,
        20,
        rewardSearch.trim() || undefined,
        rewardStatus === "" ? undefined : rewardStatus === "active",
      ),
    enabled: !isTransferPolicy && !isCreatePage,
  });
  const transferHistoryQuery = useQuery({
    queryKey: [
      "wallet-policies",
      "transfer-history",
      historyPage,
      transferQueryText.trim(),
      transferStatus,
    ],
    queryFn: () =>
      walletTransferSettingsHistory(
        historyPage,
        20,
        transferQueryText.trim() || undefined,
        transferStatus === "" ? undefined : transferStatus === "active",
      ),
    enabled: isTransferPolicy && !isCreatePage,
  });

  const [rewardDraft, setRewardDraft] = useState({
    name: "Signup reward",
    triggerType: "SIGNUP" as "SIGNUP" | "REFERRAL" | "WALLET_CREDIT",
    enabled: false,
    rewardAmount: "0",
    thresholdAmount: "",
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
      const defaultEffectiveFrom = getTodayDateValue();
      const savedEffectiveFrom = toLocalDateValue(
        rewardQuery.data.effectiveFrom,
      );
      const effectiveFrom =
        savedEffectiveFrom >= defaultEffectiveFrom
          ? savedEffectiveFrom
          : defaultEffectiveFrom;
      const savedEffectiveUntil = toLocalDateValue(
        rewardQuery.data.effectiveUntil,
        true,
      );
      setRewardDraft({
        name: "Signup reward",
        triggerType: "SIGNUP",
        enabled: rewardQuery.data.enabled,
        rewardAmount: String(rewardQuery.data.rewardAmount),
        thresholdAmount: "",
        currency: rewardQuery.data.currency,
        effectiveFrom,
        effectiveUntil:
          savedEffectiveUntil && savedEffectiveUntil >= effectiveFrom
            ? savedEffectiveUntil
            : "",
        reason: rewardQuery.data.reason ?? "",
      });
    }
  }, [rewardQuery.data]);

  useEffect(() => {
    if (transferQuery.data) {
      const defaultEffectiveFrom = getMinimumEffectiveDateTime();
      const savedEffectiveFrom = toLocalInputValue(
        transferQuery.data.effectiveFrom,
      );
      const effectiveFrom =
        savedEffectiveFrom >= defaultEffectiveFrom
          ? savedEffectiveFrom
          : defaultEffectiveFrom;
      const savedEffectiveUntil = toLocalInputValue(
        transferQuery.data.effectiveUntil,
      );
      setTransferDraft({
        enabled: transferQuery.data.enabled,
        maxSingleTransferAmount: transferQuery.data.maxSingleTransferAmount,
        dailyOutgoingLimitAmount: transferQuery.data.dailyOutgoingLimitAmount,
        currency: transferQuery.data.currency,
        effectiveFrom,
        effectiveUntil:
          savedEffectiveUntil &&
          savedEffectiveUntil >= getMinimumEffectiveUntil(effectiveFrom)
            ? savedEffectiveUntil
            : "",
        reason: transferQuery.data.reason ?? "",
      });
    }
  }, [transferQuery.data]);

  const saveRewardMutation = useMutation({
    mutationFn: async () =>
      createPromotionalRewardCampaign({
        name: rewardDraft.name,
        triggerType: rewardDraft.triggerType,
        enabled: rewardDraft.enabled,
        rewardAmount: Number(rewardDraft.rewardAmount),
        thresholdAmount: rewardDraft.triggerType === "WALLET_CREDIT" ? Number(rewardDraft.thresholdAmount) : null,
        currency: rewardDraft.currency,
        effectiveFrom: campaignStartIso(rewardDraft.effectiveFrom),
        effectiveUntil: rewardDraft.effectiveUntil
          ? campaignEndExclusiveIso(rewardDraft.effectiveUntil)
          : null,
        reason: rewardDraft.reason || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-policies"] });
      navigate(listPath);
    },
  });

  const saveTransferMutation = useMutation({
    mutationFn: async () =>
      saveWalletTransferSettings({
        enabled: transferDraft.enabled,
        maxSingleTransferAmount: Number(transferDraft.maxSingleTransferAmount),
        dailyOutgoingLimitAmount: Number(
          transferDraft.dailyOutgoingLimitAmount,
        ),
        currency: transferDraft.currency,
        effectiveFrom: toIsoValue(transferDraft.effectiveFrom),
        effectiveUntil: transferDraft.effectiveUntil
          ? toIsoValue(transferDraft.effectiveUntil)
          : null,
        reason: transferDraft.reason || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-policies"] });
      navigate(listPath);
    },
  });

  const rewardStatusMutation = useMutation({
    mutationFn: ({
      policyId,
      enabled,
    }: {
      policyId: number;
      enabled: boolean;
    }) => setSignupRewardPolicyStatus(policyId, enabled),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-policies"] });
    },
  });

  const rewardHistory = rewardHistoryQuery.data?.content ?? [];
  const transferHistory = transferHistoryQuery.data?.content ?? [];

  const activePolicyQuery = isTransferPolicy ? transferQuery : rewardQuery;

  if (activePolicyQuery.isLoading) {
    return (
      <StatusState
        title={
          isTransferPolicy
            ? t("Loading transfer policy")
            : t("Loading signup reward")
        }
        loading
        message={t("Fetching wallet policy settings")}
      />
    );
  }

  if (activePolicyQuery.error && !activePolicyQuery.data) {
    return (
      <StatusState
        title={
          isTransferPolicy
            ? t("Unable to load transfer policy")
            : t("Unable to load signup reward")
        }
        message={t(
          getUserFacingErrorMessage(activePolicyQuery.error) ??
            "Something went wrong. Please try again.",
        )}
        actionLabel={t("Retry")}
        onAction={() => activePolicyQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <AdminPageHeader
        title={isCreatePage
          ? isTransferPolicy ? t("Add transfer policy") : t("Add promotional campaign")
          : isTransferPolicy ? t("Transfer policy") : t("Promotional rewards")}
        refresh={!isCreatePage ? (
          isTransferPolicy
            ? {
                isFetching:
                  transferHistoryQuery.isFetching || transferQuery.isFetching,
                failed:
                  transferHistoryQuery.isRefetchError ||
                  transferQuery.isRefetchError,
                error: transferHistoryQuery.error ?? transferQuery.error,
                onRefresh: () =>
                  Promise.all([
                    transferHistoryQuery.refetch(),
                    transferQuery.refetch(),
                  ]),
              }
            : {
                isFetching:
                  rewardHistoryQuery.isFetching || rewardQuery.isFetching,
                failed:
                  rewardHistoryQuery.isRefetchError ||
                  rewardQuery.isRefetchError,
                error: rewardHistoryQuery.error ?? rewardQuery.error,
                onRefresh: () =>
                  Promise.all([
                    rewardHistoryQuery.refetch(),
                    rewardQuery.refetch(),
                  ]),
              }
        ) : undefined}
        description={
          isCreatePage ? undefined : isTransferPolicy
            ? t(
                "Controls the player-to-player transfer limit and daily outgoing cap.",
              )
            : t(
                "Configure signup, referral, and verified wallet credit bonus campaigns.",
              )
        }
        actions={!isCreatePage ? (
          <Button
            variant="contained"
            onClick={() => {
              if (isTransferPolicy) saveTransferMutation.reset();
              else saveRewardMutation.reset();
              navigate(`${listPath}/new`);
            }}
          >
            {isTransferPolicy
              ? t("Add transfer policy")
              : t("Add promotional campaign")}
          </Button>
        ) : undefined}
      />

      <Grid container spacing={2}>
        {!isTransferPolicy && isCreatePage ? (
          <Grid item xs={12}>
            <Card
              elevation={0}
              sx={{ border: "1px solid", borderColor: "divider" }}
            >
              <CardContent>
                <Stack spacing={2}>
                  <Grid container spacing={2}>
                    <Grid item xs={12}>
                      <TextField select label={t("Campaign name")} value={rewardDraft.triggerType}
                        onChange={(event) => {
                          const triggerType = event.target.value as typeof rewardDraft.triggerType;
                          const name = triggerType === "SIGNUP" ? "Signup reward" : triggerType === "REFERRAL" ? "Referral reward" : "Wallet credit reward";
                          setRewardDraft({ ...rewardDraft, triggerType, name });
                        }} fullWidth>
                        <MenuItem value="SIGNUP">{t("Signup reward")}</MenuItem>
                        <MenuItem value="REFERRAL">{t("Referral reward")}</MenuItem>
                        <MenuItem value="WALLET_CREDIT">{t("Wallet credit reward")}</MenuItem>
                      </TextField>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <FormControl fullWidth>
                        <InputLabel id="signup-reward-status-label">
                          {t("Status")}
                        </InputLabel>
                        <Select
                          labelId="signup-reward-status-label"
                          label={t("Status")}
                          value={rewardDraft.enabled ? "active" : "inactive"}
                          onChange={(event) =>
                            setRewardDraft({
                              ...rewardDraft,
                              enabled: event.target.value === "active",
                            })
                          }
                        >
                          <MenuItem value="active">{t("Active")}</MenuItem>
                          <MenuItem value="inactive">{t("Inactive")}</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label={t("Reward amount")}
                        type="number"
                        inputProps={{ min: 0.01, step: 0.01 }}
                        value={rewardDraft.rewardAmount}
                        onChange={(event) =>
                          setRewardDraft({
                            ...rewardDraft,
                            rewardAmount: event.target.value,
                          })
                        }
                        fullWidth
                      />
                    </Grid>
                    {rewardDraft.triggerType === "WALLET_CREDIT" ? (
                      <Grid item xs={12} sm={6}>
                        <TextField label={t("Qualifying wallet credits")} type="number" value={rewardDraft.thresholdAmount}
                          onChange={(event) => setRewardDraft({ ...rewardDraft, thresholdAmount: event.target.value })}
                          inputProps={{ min: 0.01, step: 0.01 }}
                          helperText={t("Reward is granted once verified ETB deposits posted during the campaign reach this amount.")} fullWidth />
                      </Grid>
                    ) : null}
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label={t("Currency")}
                        value={rewardDraft.currency}
                        disabled
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label={t("Effective from")}
                        type="date"
                        value={rewardDraft.effectiveFrom}
                        onChange={(event) =>
                          setRewardDraft({
                            ...rewardDraft,
                            effectiveFrom: event.target.value,
                          })
                        }
                        inputProps={{ min: getTodayDateValue() }}
                        helperText={t("Campaign dates use the Africa/Addis_Ababa timezone. Start dates cannot be in the past.")}
                        fullWidth
                        InputLabelProps={{ shrink: true }}
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label={t("Effective until")}
                        type="date"
                        value={rewardDraft.effectiveUntil}
                        onChange={(event) =>
                          setRewardDraft({
                            ...rewardDraft,
                            effectiveUntil: event.target.value,
                          })
                        }
                        inputProps={{
                          min: rewardDraft.effectiveFrom || getTodayDateValue(),
                        }}
                        helperText={t(
                          "Leave blank for no expiry. The end date is included.",
                        )}
                        fullWidth
                        InputLabelProps={{ shrink: true }}
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField
                        label={t("Reason")}
                        value={rewardDraft.reason}
                        onChange={(event) =>
                          setRewardDraft({
                            ...rewardDraft,
                            reason: event.target.value,
                          })
                        }
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
                        saveRewardMutation.isPending ||
                        !isValidRewardAmount(rewardDraft.rewardAmount) ||
                        !isValidRewardDateRange(
                          rewardDraft.effectiveFrom,
                          rewardDraft.effectiveUntil,
                        ) || (rewardDraft.triggerType === "WALLET_CREDIT" && !isValidRewardAmount(rewardDraft.thresholdAmount))
                      }
                    >
                      {t("Create campaign")}
                    </Button>
                    <Button
                      onClick={() => navigate(listPath)}
                      disabled={saveRewardMutation.isPending}
                    >
                      {t("Cancel")}
                    </Button>
                  </Stack>
                  {saveRewardMutation.isError ? (
                    <Alert severity="error" role="alert">
                      {t(
                        getUserFacingErrorMessage(saveRewardMutation.error) ??
                          "Something went wrong. Please try again.",
                      )}
                    </Alert>
                  ) : null}
                  <Alert severity="info">
                    {t(
                      rewardDraft.triggerType === "SIGNUP" ? "Signup rewards are credited once after player registration." : rewardDraft.triggerType === "REFERRAL" ? "Referral rewards are credited to the referring player when a new player registers with their referral code." : "Wallet credit rewards are credited once after verified deposits reach the configured threshold.",
                    )}
                  </Alert>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ) : null}

        {isTransferPolicy && isCreatePage ? (
          <Grid item xs={12}>
            <Card
              elevation={0}
              sx={{ border: "1px solid", borderColor: "divider" }}
            >
              <CardContent>
                <Stack spacing={2}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={transferDraft.enabled}
                        onChange={(event) =>
                          setTransferDraft({
                            ...transferDraft,
                            enabled: event.target.checked,
                          })
                        }
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
                          setTransferDraft({
                            ...transferDraft,
                            maxSingleTransferAmount: event.target.value,
                          })
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
                          setTransferDraft({
                            ...transferDraft,
                            dailyOutgoingLimitAmount: event.target.value,
                          })
                        }
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label={t("Currency")}
                        value={transferDraft.currency}
                        onChange={(event) =>
                          setTransferDraft({
                            ...transferDraft,
                            currency: event.target.value.toUpperCase(),
                          })
                        }
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label={t("Effective from")}
                        type="datetime-local"
                        value={transferDraft.effectiveFrom}
                        onChange={(event) =>
                          setTransferDraft({
                            ...transferDraft,
                            effectiveFrom: event.target.value,
                          })
                        }
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
                        onChange={(event) =>
                          setTransferDraft({
                            ...transferDraft,
                            effectiveUntil: event.target.value,
                          })
                        }
                        inputProps={{
                          min: getMinimumEffectiveUntil(
                            transferDraft.effectiveFrom,
                          ),
                        }}
                        helperText={t(
                          "Leave blank for no expiry. If set, the end time must be in the future.",
                        )}
                        fullWidth
                        InputLabelProps={{ shrink: true }}
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField
                        label={t("Reason")}
                        value={transferDraft.reason}
                        onChange={(event) =>
                          setTransferDraft({
                            ...transferDraft,
                            reason: event.target.value,
                          })
                        }
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
                      disabled={
                        saveTransferMutation.isPending ||
                        !isValidPolicyDateRange(
                          transferDraft.effectiveFrom,
                          transferDraft.effectiveUntil,
                        )
                      }
                    >
                      {t("Save transfer policy")}
                    </Button>
                    <Button
                      onClick={() => navigate(listPath)}
                      disabled={saveTransferMutation.isPending}
                    >
                      {t("Cancel")}
                    </Button>
                  </Stack>
                  {saveTransferMutation.isError ? (
                    <Alert severity="error" role="alert">
                      {t(
                        getUserFacingErrorMessage(saveTransferMutation.error) ??
                          "Something went wrong. Please try again.",
                      )}
                    </Alert>
                  ) : null}
                  <Alert
                    severity={
                      transferQuery.data?.activeNow ? "success" : "info"
                    }
                  >
                    {transferQuery.data?.activeNow
                      ? t("A transfer policy is active now.")
                      : t("No active transfer policy is currently in effect.")}
                  </Alert>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ) : null}
      </Grid>

      {isTransferPolicy && !isCreatePage ? (
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Card
              elevation={0}
              sx={{ border: "1px solid", borderColor: "divider" }}
            >
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  {t("Transfer policy history")}
                </Typography>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  spacing={2}
                  sx={{ mb: 2 }}
                >
                  <TextField
                    label={t("Search amount, currency, or reason")}
                    value={transferQueryText}
                    onChange={(event) => {
                      setTransferQueryText(event.target.value);
                      setHistoryPage(0);
                    }}
                    fullWidth
                  />
                  <TextField
                    select
                    label={t("Status")}
                    value={transferStatus}
                    onChange={(event) => {
                      setTransferStatus(event.target.value);
                      setHistoryPage(0);
                    }}
                    sx={{ minWidth: { xs: "100%", sm: 180 } }}
                  >
                    <MenuItem value="">{t("All statuses")}</MenuItem>
                    <MenuItem value="active">{t("Active")}</MenuItem>
                    <MenuItem value="inactive">{t("Inactive")}</MenuItem>
                  </TextField>
                </Stack>
                {transferHistoryQuery.error && !transferHistoryQuery.data ? (
                  <Alert severity="error">
                    {t(
                      getUserFacingErrorMessage(transferHistoryQuery.error) ??
                        "Something went wrong. Please try again.",
                    )}
                  </Alert>
                ) : null}
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small" aria-label={t("Transfer policy history")}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell align="right">
                          {t("Max single transfer")}
                        </TableCell>
                        <TableCell align="right">
                          {t("Daily outgoing limit")}
                        </TableCell>
                        <TableCell>{t("Currency")}</TableCell>
                        <TableCell>{t("Effective from")}</TableCell>
                        <TableCell>{t("Effective until")}</TableCell>
                        <TableCell>{t("Reason")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {transferHistory.map((entry) => (
                        <TableRow key={entry.id} hover>
                          <TableCell>
                            <Chip
                              label={translateAdminStatus(
                                getPolicyStatus(
                                  entry.enabled,
                                  entry.activeNow,
                                  entry.effectiveFrom,
                                  entry.effectiveUntil,
                                ),
                                t,
                              )}
                              size="small"
                              color={getPolicyStatusColor(
                                getPolicyStatus(
                                  entry.enabled,
                                  entry.activeNow,
                                  entry.effectiveFrom,
                                  entry.effectiveUntil,
                                ),
                              )}
                            />
                          </TableCell>
                          <TableCell align="right">
                            {entry.maxSingleTransferAmount}
                          </TableCell>
                          <TableCell align="right">
                            {entry.dailyOutgoingLimitAmount}
                          </TableCell>
                          <TableCell>{entry.currency}</TableCell>
                          <TableCell>
                            {formatEthiopianDateTime(entry.effectiveFrom)}
                          </TableCell>
                          <TableCell>
                            {entry.effectiveUntil
                              ? formatEthiopianDateTime(entry.effectiveUntil)
                              : t("No expiry")}
                          </TableCell>
                          <TableCell>{entry.reason ?? t("n/a")}</TableCell>
                        </TableRow>
                      ))}
                      {!transferHistoryQuery.isLoading &&
                      !transferHistoryQuery.error &&
                      transferHistory.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={7}>
                            {t("No transfer policy history yet.")}
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </Box>
                <AdminTablePagination
                  component="div"
                  dataLoaded={!!transferHistoryQuery.data}
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
      ) : !isCreatePage ? (
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Card
              elevation={0}
              sx={{ border: "1px solid", borderColor: "divider" }}
            >
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  {t("Promotional campaigns")}
                </Typography>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  spacing={2}
                  sx={{ mb: 2 }}
                >
                  <TextField
                    label={t("Search amount, currency, or reason")}
                    value={rewardSearch}
                    onChange={(event) => {
                      setRewardSearch(event.target.value);
                      setHistoryPage(0);
                    }}
                    fullWidth
                  />
                  <TextField
                    select
                    label={t("Status")}
                    value={rewardStatus}
                    onChange={(event) => {
                      setRewardStatus(event.target.value);
                      setHistoryPage(0);
                    }}
                    sx={{ minWidth: { xs: "100%", sm: 180 } }}
                  >
                    <MenuItem value="">{t("All statuses")}</MenuItem>
                    <MenuItem value="active">{t("Active")}</MenuItem>
                    <MenuItem value="inactive">{t("Inactive")}</MenuItem>
                  </TextField>
                </Stack>
                {rewardHistoryQuery.error && !rewardHistoryQuery.data ? (
                  <Alert severity="error">
                    {t(
                      getUserFacingErrorMessage(rewardHistoryQuery.error) ??
                        "Something went wrong. Please try again.",
                    )}
                  </Alert>
                ) : null}
                {rewardStatusMutation.error ? (
                  <Alert severity="error" sx={{ mb: 1 }}>
                    {t(
                      getUserFacingErrorMessage(rewardStatusMutation.error) ??
                        "Something went wrong. Please try again.",
                    )}
                  </Alert>
                ) : null}
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small" aria-label={t("Reward history")}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Campaign")}</TableCell>
                        <TableCell>{t("Trigger")}</TableCell>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell align="right">
                          {t("Reward amount")}
                        </TableCell>
                        <TableCell>{t("Currency")}</TableCell>
                        <TableCell>{t("Effective from")}</TableCell>
                        <TableCell>{t("Effective until")}</TableCell>
                        <TableCell>{t("Reason")}</TableCell>
                        <TableCell>{t("Actions")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rewardHistory.map((entry) => (
                        <TableRow key={entry.id} hover>
                          <TableCell>{entry.name}</TableCell>
                          <TableCell>
                            {entry.triggerType === "SIGNUP" ? t("Signup reward")
                              : entry.triggerType === "REFERRAL" ? t("Referral reward")
                              : entry.triggerType === "WALLET_CREDIT" ? `${t("Wallet credit reward")} · ${entry.thresholdAmount ?? "—"} ${entry.currency}`
                              : t("Manual")}
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={
                                entry.enabled ? t("Active") : t("Inactive")
                              }
                              size="small"
                              color={entry.enabled ? "success" : "default"}
                            />
                          </TableCell>
                          <TableCell align="right">
                            {entry.rewardAmount}
                          </TableCell>
                          <TableCell>{entry.currency}</TableCell>
                          <TableCell>
                            {formatPolicyDate(entry.effectiveFrom)}
                          </TableCell>
                          <TableCell>
                            {entry.effectiveUntil
                              ? formatPolicyDate(entry.effectiveUntil, true)
                              : t("No expiry")}
                          </TableCell>
                          <TableCell>{entry.reason ?? t("n/a")}</TableCell>
                          <TableCell>
                            <Button
                              size="small"
                              disabled={rewardStatusMutation.isPending}
                              onClick={() =>
                                rewardStatusMutation.mutate({
                                  policyId: entry.id!,
                                  enabled: !entry.enabled,
                                })
                              }
                            >
                              {entry.enabled
                                ? t("Make inactive")
                                : t("Make active")}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                      {!rewardHistoryQuery.isLoading &&
                      !rewardHistoryQuery.error &&
                      rewardHistory.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={9}>
                            {t("No promotional campaigns yet.")}
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </Box>
                <AdminTablePagination
                  component="div"
                  dataLoaded={!!rewardHistoryQuery.data}
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
        </Grid>
      ) : null}
    </Stack>
  );
}
