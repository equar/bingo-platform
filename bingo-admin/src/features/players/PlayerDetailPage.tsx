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
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useParams } from "react-router-dom";
import { adjustWallet, playerDetail } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

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

export function PlayerDetailPage() {
  const { playerId } = useParams();
  const id = Number(playerId);
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<AdjustmentDraft | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const idempotency = useIdempotencyKey();

  const playerQuery = useQuery({
    queryKey: ["player", id],
    queryFn: () => playerDetail(id),
    enabled: Number.isFinite(id),
  });
  const adjustmentMutation = useMutation({
    mutationFn: async (body: AdjustmentDraft) =>
      adjustWallet(id, {
        amount: Number(body.amount),
        currency: body.currency,
        reason: body.reason,
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

  const player = playerQuery.data;
  const showLedgerHint = useMemo(
    () => Boolean(player?.walletTransactionCount || player?.depositClaimCount || player?.supportTicketCount),
    [player],
  );

  if (playerQuery.isLoading) {
    return <StatusState title="Loading player" loading message="Fetching player profile" />;
  }

  if (playerQuery.error || !player) {
    return (
      <StatusState
        title="Unable to load player"
        message={(playerQuery.error as Error | undefined)?.message ?? "Player not found"}
        actionLabel="Retry"
        onAction={() => playerQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">{player.displayName}</Typography>
          <Typography color="text.secondary">
            {player.normalizedPhone} · {player.roles.join(", ")}
          </Typography>
        </div>
        <Button variant="contained" onClick={() => setDraft({ ...emptyDraft })}>
          Adjust wallet
        </Button>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Typography variant="overline">Wallet transactions</Typography>
              <Typography variant="h4">{player.walletTransactionCount}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Typography variant="overline">Deposits</Typography>
              <Typography variant="h4">{player.depositClaimCount}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Typography variant="overline">Support tickets</Typography>
              <Typography variant="h4">{player.supportTicketCount}</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {showLedgerHint ? (
        <Alert severity="info">
          The backend exposes aggregated activity counts on the player profile, but not a separate admin wallet ledger
          endpoint. Use claims, deposits, and audit pages for transaction history.
        </Alert>
      ) : null}

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={1.25}>
            <Typography variant="h6">Player summary</Typography>
            <Typography>Created: {new Date(player.createdAt).toLocaleString()}</Typography>
            <Typography>Updated: {new Date(player.updatedAt).toLocaleString()}</Typography>
            <Typography>Status: {player.status}</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label={`Wallet tx ${player.walletTransactionCount}`} size="small" />
              <Chip label={`Deposits ${player.depositClaimCount}`} size="small" />
              <Chip label={`Support ${player.supportTicketCount}`} size="small" />
              <Chip label={`Tickets ${player.ticketCount}`} size="small" />
              <Chip label={`Wins ${player.winClaimCount}`} size="small" />
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Dialog open={Boolean(draft)} onClose={() => setDraft(null)} fullWidth maxWidth="sm">
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
              fullWidth
            />
            <TextField
              label="Reason"
              value={draft?.reason ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, reason: event.target.value })}
              fullWidth
              multiline
              minRows={2}
              required
            />
            <TextField
              label="Currency"
              value={draft?.currency ?? "ETB"}
              onChange={(event) => draft && setDraft({ ...draft, currency: event.target.value.toUpperCase() })}
              fullWidth
            />
            <TextField
              label="Reference"
              value={draft?.reference ?? ""}
              onChange={(event) => draft && setDraft({ ...draft, reference: event.target.value })}
              fullWidth
              helperText="Optional note for the audit trail"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => setConfirmOpen(true)}
            disabled={!draft?.reason || adjustmentMutation.isPending}
          >
            Review
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        title="Confirm wallet adjustment"
        description={`Apply a ${draft?.direction?.toLowerCase()} of ${draft?.amount} to ${player.displayName}? The backend will enforce authorization and idempotency.`}
        confirmLabel="Confirm adjustment"
        cancelLabel="Back"
        confirming={adjustmentMutation.isPending}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => draft && adjustmentMutation.mutate(draft)}
      />
    </Stack>
  );
}
