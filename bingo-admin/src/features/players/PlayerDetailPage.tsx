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
import { useParams } from "react-router-dom";
import { Link as RouterLink } from "react-router-dom";
import { adjustWallet, playerDetail, updatePlayerStatus } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";
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
  return (
    <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
      <ButtonBase
        onClick={onClick}
        sx={{ display: "block", height: "100%", textAlign: "left", width: "100%" }}
        aria-label={`View ${title.toLowerCase()} details`}
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
  const idempotency = useIdempotencyKey();

  const playerQuery = useQuery({
    queryKey: ["player", id],
    queryFn: () => playerDetail(id),
    enabled: validPlayerId,
  });
  const adjustmentMutation = useMutation({
    mutationFn: async (body: AdjustmentDraft) =>
      adjustWallet(id, {
        amount: Math.abs(Number(body.amount)) * (body.direction === "DEBIT" ? -1 : 1),
        currency: player?.walletCurrency ?? body.currency,
        reason: adjustmentReason(body),
        idempotencyKey: idempotency.getKey(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["player", id] });
      await queryClient.invalidateQueries({ queryKey: ["players"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      idempotency.resetKey();
      setDraft(null);
      setConfirmOpen(false);
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
  const backButton = (
    <Button component={RouterLink} to="/players" size="small" variant="outlined">
      Back to players
    </Button>
  );

  if (!validPlayerId) {
    return (
      <Stack spacing={2} alignItems="flex-start">
        {backButton}
        <StatusState title="Invalid player ID" message="Open a player profile using a valid player ID." />
      </Stack>
    );
  }

  if (playerQuery.isLoading) {
    return (
      <Stack spacing={2} alignItems="flex-start">
        {backButton}
        <StatusState title="Loading player" loading message="Fetching player profile" />
      </Stack>
    );
  }

  if (playerQuery.error || !player) {
    return (
      <Stack spacing={2} alignItems="flex-start">
        {backButton}
        <StatusState
          title="Unable to load player"
          message={(playerQuery.error as Error | undefined)?.message ?? "Player not found"}
          actionLabel="Retry"
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
              label={player.status}
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
            Player #{player.id} · {player.normalizedPhone} · {player.email ?? "No email provided"}
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
            Change account status
          </Button>
          <Button
            variant="contained"
            onClick={() => {
              adjustmentMutation.reset();
              idempotency.resetKey();
              setDraft({ ...emptyDraft, currency: player.walletCurrency ?? "ETB" });
            }}
            disabled={!player.roles.includes("PLAYER")}
          >
            Adjust wallet
          </Button>
        </Stack>
      </Box>

      {statusMutation.isError ? (
        <Alert severity="error" onClose={() => statusMutation.reset()}>
          {(statusMutation.error as Error).message || "Unable to update player status."}
        </Alert>
      ) : null}

      {adjustmentMutation.isError ? (
        <Alert severity="error" onClose={() => adjustmentMutation.reset()}>
          {(adjustmentMutation.error as Error).message || "Wallet adjustment failed."}
        </Alert>
      ) : null}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Wallet transactions"
            value={player.walletTransactionCount}
            description="View wallet history"
            onClick={() => setActivity("wallet")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Deposits"
            value={player.depositClaimCount}
            description="View deposit claims"
            onClick={() => setActivity("deposits")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Support tickets"
            value={player.supportTicketCount}
            description="View support history"
            onClick={() => setActivity("support")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Available balance"
            value={
              player.walletAvailableBalance == null
                ? "No wallet"
                : `${player.walletAvailableBalance} ${player.walletCurrency ?? ""}`
            }
            description="View wallet history"
            onClick={() => setActivity("wallet")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Promotional balance"
            value={
              player.walletPromotionalBalance == null
                ? "No wallet"
                : `${player.walletPromotionalBalance} ${player.walletCurrency ?? ""}`
            }
            description="View wallet history"
            onClick={() => setActivity("wallet")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Game tickets"
            value={player.ticketCount}
            description="View purchased tickets"
            onClick={() => setActivity("tickets")}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={4}>
          <PlayerActivityCard
            title="Win claims"
            value={player.winClaimCount}
            description="View win claim history"
            onClick={() => setActivity("wins")}
          />
        </Grid>
      </Grid>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">Account details</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  Player ID
                </Typography>
                <Typography>{player.id}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  Phone
                </Typography>
                <Typography>{player.normalizedPhone}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  Email
                </Typography>
                <Typography>{player.email ?? "Not provided"}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  Roles
                </Typography>
                <Typography>{player.roles.join(", ") || "None"}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  Created
                </Typography>
                <Typography>{new Date(player.createdAt).toLocaleString()}</Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={4}>
                <Typography variant="overline" color="text.secondary">
                  Last updated
                </Typography>
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
        <DialogTitle>Change player account status</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info">
              Non-active players cannot sign in or use protected APIs. This action is recorded in the audit log.
            </Alert>
            <TextField
              select
              label="Account status"
              value={nextStatus}
              onChange={(event) => setNextStatus(event.target.value)}
              fullWidth
            >
              {["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"].map((status) => (
                <MenuItem key={status} value={status}>{status}</MenuItem>
              ))}
            </TextField>
            <TextField
              label="Reason"
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
          <Button onClick={() => setStatusDialogOpen(false)} disabled={statusMutation.isPending}>Cancel</Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => statusMutation.mutate()}
            disabled={!nextStatus || nextStatus === player.status || !statusReason.trim() || statusMutation.isPending}
          >
            {statusMutation.isPending ? "Saving..." : "Save status"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(draft)}
        onClose={() => {
          if (!adjustmentMutation.isPending) {
            setDraft(null);
            idempotency.resetKey();
          }
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Adjust wallet</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              select
              label="Direction"
              value={draft?.direction ?? "CREDIT"}
              onChange={(event) =>
                draft && setDraft({ ...draft, direction: event.target.value as AdjustmentDraft["direction"] })
              }
              fullWidth
            >
              <MenuItem value="CREDIT">Credit</MenuItem>
              <MenuItem value="DEBIT">Debit</MenuItem>
            </TextField>
            <TextField
              label="Amount"
              type="number"
              value={draft?.amount ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, amount: event.target.value })}
              inputProps={{ min: 0.01, step: 0.01 }}
              fullWidth
              required
              error={Boolean(draft?.amount && (!Number.isFinite(Number(draft.amount)) || Number(draft.amount) <= 0))}
              helperText={
                draft?.amount && (!Number.isFinite(Number(draft.amount)) || Number(draft.amount) <= 0)
                  ? "Enter an amount greater than zero."
                  : "Enter a positive amount; direction determines whether it is credited or debited."
              }
            />
            <TextField
              label="Reason"
              value={draft?.reason ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, reason: event.target.value })}
              fullWidth
              multiline
              minRows={2}
              required
              inputProps={{ maxLength: 350 }}
            />
            <TextField
              label="Currency"
              value={draft?.currency ?? "ETB"}
              disabled
              fullWidth
              helperText="Wallet currency is fixed by the platform."
            />
            <TextField
              label="Reference"
              value={draft?.reference ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, reference: event.target.value })}
              inputProps={{ maxLength: 120 }}
              fullWidth
              helperText="Optional reference included in the audit reason."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setDraft(null);
              idempotency.resetKey();
            }}
            disabled={adjustmentMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={() => setConfirmOpen(true)}
            disabled={
              !draft?.reason.trim() ||
              !Number.isFinite(Number(draft?.amount)) ||
              Number(draft?.amount) <= 0 ||
              Boolean(draft && adjustmentReason(draft).length > 500) ||
              adjustmentMutation.isPending
            }
          >
            Review
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        title="Confirm wallet adjustment"
        description={`Apply a ${draft?.direction?.toLowerCase()} of ${draft?.amount} ${player.walletCurrency ?? draft?.currency} to ${player.displayName}? This action is recorded in the wallet audit trail.`}
        confirmLabel="Confirm adjustment"
        cancelLabel="Back"
        confirming={adjustmentMutation.isPending}
        error={adjustmentMutation.isError ? adjustmentMutation.error.message : undefined}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => draft && adjustmentMutation.mutate(draft)}
      />
    </Stack>
  );
}
