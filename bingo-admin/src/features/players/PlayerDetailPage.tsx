import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
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
import { useLocation, useParams } from "react-router-dom";
import { Link as RouterLink } from "react-router-dom";
import {
  playerDetail,
  reviewWalletAdjustmentRequest,
  submitWalletAdjustmentRequest,
  updatePlayerStatus,
  walletAdjustmentHistory,
} from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { translateAdminRole, translateAdminStatus } from "../../i18n";
import { PlayerActivityDialog, type PlayerActivityType } from "./PlayerActivityDialog";

interface AdjustmentDraft {
  amount: string;
  direction: "CREDIT" | "DEBIT";
  reason: string;
  reference: string;
  currency: string;
}

const emptyDraft: AdjustmentDraft = {
  amount: "",
  direction: "CREDIT",
  reason: "",
  reference: "",
  currency: "ETB",
};

function adjustmentReason(draft: AdjustmentDraft) {
  const reason = draft.reason.trim();
  const reference = draft.reference.trim();
  return reference ? `${reason} (Reference: ${reference})` : reason;
}

function PlayerActivityCard({
  title,
  value,
  description,
  onClick,
}: {
  title: string;
  value: ReactNode;
  description: string;
  onClick: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
      <ButtonBase
        onClick={onClick}
        sx={{ display: "block", height: "100%", textAlign: "left", width: "100%" }}
        aria-label={t("View {{title}} details", { title: title.toLowerCase() })}
      >
        <Box sx={{ p: 2 }}>
          <Typography variant="overline">{title}</Typography>
          <Typography variant="h5">{value}</Typography>
          <Typography variant="caption" color="text.secondary">
            {description}
          </Typography>
        </Box>
      </ButtonBase>
    </Card>
  );
}

export function PlayerDetailPage() {
  const location = useLocation();
  const { t } = useTranslation();
  const { playerId } = useParams();
  const id = Number(playerId);
  const validPlayerId = Number.isSafeInteger(id) && id > 0;
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<AdjustmentDraft | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [activity, setActivity] = useState<PlayerActivityType | null>(null);
  const [statusDialogOpen, setStatusDialogOpen] = useState(false);
  const [nextStatus, setNextStatus] = useState("");
  const [statusReason, setStatusReason] = useState("");
  const [reviewRequestId, setReviewRequestId] = useState<number | null>(null);
  const [reviewDecision, setReviewDecision] = useState<"approve" | "reject">("approve");
  const [reviewNote, setReviewNote] = useState("");
  const [uncertainReviewRequestIds, setUncertainReviewRequestIds] = useState<Set<number>>(() => new Set());

  const playerQuery = useQuery({
    queryKey: ["player", id],
    queryFn: () => playerDetail(id),
    enabled: validPlayerId,
  });
  const adjustmentHistoryQuery = useQuery({
    queryKey: ["wallet-adjustment-history", id],
    queryFn: () => walletAdjustmentHistory(id),
    enabled: validPlayerId,
  });
  const adjustmentMutation = useMutation({
    mutationFn: async (body: AdjustmentDraft) =>
      submitWalletAdjustmentRequest(id, {
        amount: Math.abs(Number(body.amount)) * (body.direction === "DEBIT" ? -1 : 1),
        currency: player?.walletCurrency ?? body.currency,
        reason: body.reason.trim(),
        supportingReference: body.reference.trim(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-adjustment-history", id] });
      setDraft(null);
      setConfirmOpen(false);
    },
    onError: async (error) => {
      if (parseApiError(error).outcomeUnknown) {
        await queryClient.invalidateQueries({ queryKey: ["wallet-adjustment-history", id] });
      }
    },
  });
  const adjustmentReviewMutation = useMutation({
    mutationFn: async () => {
      if (reviewRequestId == null) {
        throw new Error("Missing wallet adjustment request");
      }
      return reviewWalletAdjustmentRequest(reviewRequestId, reviewDecision, reviewNote.trim());
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["wallet-adjustment-history", id] });
      await queryClient.invalidateQueries({ queryKey: ["player", id] });
      setReviewRequestId(null);
      setReviewNote("");
    },
    onError: async (error) => {
      if (parseApiError(error).outcomeUnknown && reviewRequestId != null) {
        setUncertainReviewRequestIds((current) => new Set(current).add(reviewRequestId));
        await queryClient.invalidateQueries({ queryKey: ["wallet-adjustment-history", id] });
      }
    },
  });
  const statusMutation = useMutation({
    mutationFn: () => updatePlayerStatus(id, nextStatus, statusReason.trim()),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["player", id] });
      await queryClient.invalidateQueries({ queryKey: ["players"] });
      setStatusDialogOpen(false);
      setStatusReason("");
    },
  });

  const player = playerQuery.data;
  const adjustmentOutcomeUnknown =
    adjustmentMutation.isError && parseApiError(adjustmentMutation.error).outcomeUnknown;
  const reviewOutcomeUnknown =
    reviewRequestId != null && uncertainReviewRequestIds.has(reviewRequestId);
  const backButton = (
    <Button component={RouterLink} to={`/players${location.search}`} size="small" variant="outlined">
      {t("Back to players")}</Button>
  );

  if (!validPlayerId) {
    return (
      <Stack spacing={2} alignItems="flex-start">
        {backButton}
        <StatusState title={t("Invalid player ID")} message={t("Open a player profile using a valid player ID.")} />
      </Stack>
    );
  }

  if (playerQuery.isLoading) {
    return (
      <Stack spacing={2} alignItems="flex-start">
        {backButton}
        <StatusState title={t("Loading player")} loading message={t("Fetching player profile")} />
      </Stack>
    );
  }

  if (playerQuery.error || !player) {
    return (
      <Stack spacing={2} alignItems="flex-start">
        {backButton}
        <StatusState
          title={t("Unable to load player")}
          message={t(getUserFacingErrorMessage(playerQuery.error) ?? "This item is no longer available. Refresh and try again.")}
          actionLabel={t("Retry")}
          onAction={() => playerQuery.refetch()}
        />
      </Stack>
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Box sx={{ mb: 1 }}>{backButton}</Box>
          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap">
            <Typography variant="h4">{player.displayName}</Typography>
            <Chip
              label={translateAdminStatus(player.status, t)}
              size="small"
              color={
                player.status === "ACTIVE"
                  ? "success"
                  : player.status === "PENDING"
                    ? "warning"
                    : player.status === "SUSPENDED"
                      ? "error"
                      : "default"
              }
            />
          </Stack>
          <Typography color="text.secondary">
            {t("Player #")}{player.id} {t("·")}{" "}{player.normalizedPhone} {t("·")}{" "}{player.email ?? t("No email provided")}
          </Typography>
        </div>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            onClick={() => {
              statusMutation.reset();
              setNextStatus(player.status);
              setStatusReason("");
              setStatusDialogOpen(true);
            }}
            disabled={!player.roles.includes("PLAYER")}
          >
            {t("Change account status")}</Button>
          <Button
            variant="contained"
            onClick={() => {
              adjustmentMutation.reset();
                setDraft({ ...emptyDraft, currency: player.walletCurrency ?? "ETB" });
            }}
            disabled={!player.roles.includes("PLAYER") || adjustmentOutcomeUnknown}
          >
            {t("Adjust wallet")}</Button>
        </Stack>
      </Box>

      {statusMutation.isError ? (
        <Alert severity="error" onClose={() => statusMutation.reset()}>
          {t(getUserFacingErrorMessage(statusMutation.error) ?? "Something went wrong. Please try again.")}
        </Alert>
      ) : null}

      {adjustmentMutation.isError && !confirmOpen ? (
        <Alert
          severity="error"
          onClose={adjustmentOutcomeUnknown ? undefined : () => adjustmentMutation.reset()}
        >
          {t(getUserFacingErrorMessage(adjustmentMutation.error) ?? "Something went wrong. Please try again.")}
        </Alert>
      ) : null}
      {reviewRequestId == null && uncertainReviewRequestIds.size > 0 ? (
        <Alert severity="error">
          {t(getUserFacingErrorMessage(adjustmentReviewMutation.error) ??
            "We couldn’t confirm the transaction status. Check the transaction history before trying again.")}
        </Alert>
      ) : null}
      {adjustmentMutation.isSuccess ? (
        <Alert severity="success">
          {t("Adjustment request #")}{adjustmentMutation.data.id} {t("was submitted for independent approval. The wallet balance has not changed.")}</Alert>
      ) : null}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Wallet transactions")}
            value={player.walletTransactionCount}
            description={t("View wallet history")}
            onClick={() => setActivity("wallet")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Deposits")}
            value={player.depositClaimCount}
            description={t("View deposit claims")}
            onClick={() => setActivity("deposits")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Support tickets")}
            value={player.supportTicketCount}
            description={t("View support history")}
            onClick={() => setActivity("support")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Available balance")}
            value={
              player.walletAvailableBalance == null
                ? t("No wallet")
                : `${player.walletAvailableBalance} ${player.walletCurrency ?? ""}`
            }
            description={t("View wallet history")}
            onClick={() => setActivity("wallet")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Promotional balance")}
            value={
              player.walletPromotionalBalance == null
                ? t("No wallet")
                : `${player.walletPromotionalBalance} ${player.walletCurrency ?? ""}`
            }
            description={t("View wallet history")}
            onClick={() => setActivity("wallet")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Game tickets")}
            value={player.ticketCount}
            description={t("View purchased tickets")}
            onClick={() => setActivity("tickets")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title={t("Win claims")}
            value={player.winClaimCount}
            description={t("View win claim history")}
            onClick={() => setActivity("wins")}
          />
        </Grid>
      </Grid>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={1}>
            <Typography variant="h6">{t("Wallet adjustment requests")}</Typography>
            {adjustmentHistoryQuery.isError ? (
              <Alert severity="error">{t("Unable to load adjustment history:")}{" "}{t(getUserFacingErrorMessage(adjustmentHistoryQuery.error) ?? "Something went wrong. Please try again.")}</Alert>
            ) : adjustmentHistoryQuery.isLoading ? (
              <Typography color="text.secondary">{t("Loading adjustment history...")}</Typography>
            ) : adjustmentHistoryQuery.data?.length ? (
              adjustmentHistoryQuery.data.map((request) => (
                <Box key={request.id} sx={{ borderBottom: "1px solid", borderColor: "divider", py: 1 }}>
                  <Typography>
                    {t("#")}{request.id}{t(":")}{" "}{request.amount} {request.currency} {t("·")}{" "}{translateAdminStatus(request.status, t)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t("Maker #")}{request.makerUserId}
                    {request.checkerUserId == null ? "" : ` · ${t("Checker")} #${request.checkerUserId}`}
                    {request.postingReference == null ? "" : ` · ${t("Journal")} ${request.postingReference}`}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t("Reason:")}{" "}{request.reason}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t("Supporting reference:")}{" "}{request.supportingReference}
                  </Typography>
                  {request.reviewNote ? (
                    <Typography variant="caption" color="text.secondary">
                      {t("Checker review note:")}{" "}{request.reviewNote}
                    </Typography>
                  ) : null}
                  {request.status === "PENDING" ? (
                    <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                      <Button
                        size="small"
                        disabled={uncertainReviewRequestIds.has(request.id)}
                        onClick={() => {
                          adjustmentReviewMutation.reset();
                          setReviewDecision("approve");
                          setReviewNote("");
                          setReviewRequestId(request.id);
                        }}
                      >
                        {t("Approve request")}</Button>
                      <Button
                        size="small"
                        color="error"
                        disabled={uncertainReviewRequestIds.has(request.id)}
                        onClick={() => {
                          adjustmentReviewMutation.reset();
                          setReviewDecision("reject");
                          setReviewNote("");
                          setReviewRequestId(request.id);
                        }}
                      >
                        {t("Reject request")}</Button>
                    </Stack>
                  ) : null}
                </Box>
              ))
            ) : (
              <Typography color="text.secondary">{t("No wallet adjustment requests.")}</Typography>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">{t("Account details")}</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  {t("Player ID")}</Typography>
                <Typography>{player.id}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  {t("Phone")}</Typography>
                <Typography>{player.normalizedPhone}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  {t("Email")}</Typography>
                <Typography>{player.email ?? t("Not provided")}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  {t("Roles")}</Typography>
                <Typography>{player.roles.map((role) => translateAdminRole(role, t)).join(", ") || t("None")}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  {t("Created")}</Typography>
                <Typography>{new Date(player.createdAt).toLocaleString()}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  {t("Last updated")}</Typography>
                <Typography>{new Date(player.updatedAt).toLocaleString()}</Typography>
              </Grid>
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      <PlayerActivityDialog
        key={activity ?? "closed"}
        open={activity != null}
        playerId={player.id}
        initialActivity={activity ?? "wallet"}
        onClose={() => setActivity(null)}
      />

      <Dialog
        open={statusDialogOpen}
        onClose={() => !statusMutation.isPending && setStatusDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{t("Change player account status")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info">
              {t("Non-active players cannot sign in or use protected APIs. This action is recorded in the audit log.")}</Alert>
            <TextField
              select
              label={t("Account status")}
              value={nextStatus}
              onChange={(event) => setNextStatus(event.target.value)}
              fullWidth
            >
              {["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"].map((status) => (
                <MenuItem key={status} value={status}>{translateAdminStatus(status, t)}</MenuItem>
              ))}
            </TextField>
            <TextField
              label={t("Reason")}
              value={statusReason}
              onChange={(event) => setStatusReason(event.target.value)}
              inputProps={{ maxLength: 500 }}
              required
              multiline
              minRows={2}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStatusDialogOpen(false)} disabled={statusMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => statusMutation.mutate()}
            disabled={!nextStatus || nextStatus === player.status || !statusReason.trim() || statusMutation.isPending}
          >
            {statusMutation.isPending ? t("Saving...") : t("Save status")}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(draft)}
        onClose={() => {
          if (!adjustmentMutation.isPending) {
            setDraft(null);
          }
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{t("Adjust wallet")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              select
              label={t("Direction")}
              value={draft?.direction ?? "CREDIT"}
              onChange={(event) =>
                draft && setDraft({ ...draft, direction: event.target.value as AdjustmentDraft["direction"] })
              }
              fullWidth
            >
              <MenuItem value="CREDIT">{t("Credit")}</MenuItem>
              <MenuItem value="DEBIT">{t("Debit")}</MenuItem>
            </TextField>
            <TextField
              label={t("Amount")}
              type="number"
              value={draft?.amount ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, amount: event.target.value })}
              inputProps={{ min: 0.01, step: 0.01 }}
              fullWidth
              required
              error={Boolean(draft?.amount && (!Number.isFinite(Number(draft.amount)) || Number(draft.amount) <= 0))}
              helperText={
                draft?.amount && (!Number.isFinite(Number(draft.amount)) || Number(draft.amount) <= 0)
                  ? t("Enter an amount greater than zero.")
                  : t("Enter a positive amount; direction determines whether it is credited or debited.")
              }
            />
            <TextField
              label={t("Reason")}
              value={draft?.reason ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, reason: event.target.value })}
              fullWidth
              multiline
              minRows={2}
              required
              inputProps={{ maxLength: 350 }}
            />
            <TextField
              label={t("Currency")}
              value={draft?.currency ?? "ETB"}
              disabled
              fullWidth
              helperText={t("Wallet currency is fixed by the platform.")}
            />
            <TextField
              label={t("Reference")}
              value={draft?.reference ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, reference: event.target.value })}
              inputProps={{ maxLength: 120 }}
              fullWidth
              required
              helperText={t("Required supporting reference for checker review.")}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setDraft(null);
            }}
            disabled={adjustmentMutation.isPending}
          >
            {t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => setConfirmOpen(true)}
            disabled={
              !draft?.reason.trim() ||
              !draft?.reference.trim() ||
              !Number.isFinite(Number(draft?.amount)) ||
              Number(draft?.amount) <= 0 ||
              Boolean(draft && adjustmentReason(draft).length > 500) ||
              adjustmentMutation.isPending
            }
          >
            {t("Review")}</Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={reviewRequestId != null}
        onClose={() => !adjustmentReviewMutation.isPending && setReviewRequestId(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{reviewDecision === "approve" ? t("Approve adjustment request") : t("Reject adjustment request")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning">
              {t("The maker cannot review their own request. Approval posts once; rejection leaves the wallet unchanged.")}</Alert>
            {adjustmentReviewMutation.isError ? (
              <Alert severity="error">{t(getUserFacingErrorMessage(adjustmentReviewMutation.error) ?? "Something went wrong. Please try again.")}</Alert>
            ) : null}
            <TextField
              label={t("Checker review note")}
              value={reviewNote}
              onChange={(event) => setReviewNote(event.target.value)}
              disabled={reviewOutcomeUnknown}
              inputProps={{ maxLength: 500 }}
              multiline
              minRows={2}
              required
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReviewRequestId(null)} disabled={adjustmentReviewMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            color={reviewDecision === "approve" ? "primary" : "error"}
            onClick={() => adjustmentReviewMutation.mutate()}
            disabled={!reviewNote.trim() || adjustmentReviewMutation.isPending || reviewOutcomeUnknown}
          >
            {reviewDecision === "approve" ? t("Approve") : t("Reject")}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        title={t("Submit wallet adjustment request")}
        description={t(
          "Submit a {{direction}} request of {{amount}} {{currency}} for {{name}}? A different finance approver must review it; no balance changes until approved.",
          {
            direction: draft?.direction?.toLowerCase() ?? "",
            amount: draft?.amount ?? "",
            currency: player.walletCurrency ?? draft?.currency ?? "",
            name: player.displayName,
          },
        )}
        confirmLabel={t("Submit request")}
        cancelLabel={t("Back")}
        confirming={adjustmentMutation.isPending}
        disableConfirm={adjustmentOutcomeUnknown}
        error={adjustmentMutation.isError ? t(getUserFacingErrorMessage(adjustmentMutation.error) ?? "Something went wrong. Please try again.") : undefined}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => draft && adjustmentMutation.mutate(draft)}
      />
    </Stack>
  );
}
