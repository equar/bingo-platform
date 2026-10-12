import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
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
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { approveDeposit, deposits, rejectDeposit } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

export function DepositsPage() {
  const queryClient = useQueryClient();
  const depositsQuery = useQuery({ queryKey: ["deposits"], queryFn: () => deposits(0, 50) });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [mode, setMode] = useState<"APPROVE" | "REJECT">("APPROVE");
  const idempotency = useIdempotencyKey();

  const actionMutation = useMutation({
    mutationFn: async () => {
      if (selectedId == null) {
        throw new Error("Missing deposit id");
      }
      if (mode === "APPROVE") {
        return approveDeposit(selectedId, { reviewNote: comment, idempotencyKey: idempotency.getKey() });
      }
      return rejectDeposit(selectedId, { reviewNote: comment, idempotencyKey: idempotency.getKey() });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["deposits"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      idempotency.resetKey();
      setComment("");
      setConfirmOpen(false);
      setSelectedId(null);
    },
  });

  if (depositsQuery.isLoading) {
    return <StatusState title="Loading deposits" loading message="Fetching deposit queue" />;
  }

  if (depositsQuery.error) {
    return (
      <StatusState
        title="Unable to load deposits"
        message={(depositsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => depositsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Deposits</Typography>
        <Typography color="text.secondary">Verify payment references, snapshots, and review history.</Typography>
      </Box>

      {(depositsQuery.data?.content ?? []).length === 0 ? (
        <Alert severity="info">No deposit reviews are pending.</Alert>
      ) : null}

      <Grid container spacing={2}>
        {(depositsQuery.data?.content ?? []).map((deposit) => (
          <Grid item xs={12} md={6} key={deposit.id}>
            <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardContent>
                <Stack spacing={1.25}>
                  <Box display="flex" justifyContent="space-between" gap={2}>
                    <div>
                      <Typography variant="h6">Deposit #{deposit.id}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Player {deposit.playerId}
                      </Typography>
                    </div>
                    <Chip label={deposit.status} color="warning" size="small" />
                  </Box>
                  <Typography>
                    Submitted cash: {deposit.submittedCashAmount} {deposit.currency}
                  </Typography>
                  <Typography>Credit amount: {deposit.calculatedCreditAmount}</Typography>
                  <Typography>Telebirr ref: {deposit.normalizedTelebirrReference}</Typography>
                  <Typography>Review note: {deposit.reviewNote ?? "n/a"}</Typography>
                  <Typography variant="body2">Operation: {deposit.operationId}</Typography>
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="contained"
                      onClick={() => {
                        setMode("APPROVE");
                        setSelectedId(deposit.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Approve
                    </Button>
                    <Button
                      variant="outlined"
                      onClick={() => {
                        setMode("REJECT");
                        setSelectedId(deposit.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Reject
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{mode === "APPROVE" ? "Approve deposit" : "Reject deposit"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label={mode === "APPROVE" ? "Review note" : "Rejection reason"}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              fullWidth
              multiline
              minRows={3}
              required
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => actionMutation.mutate()}
            disabled={!comment || actionMutation.isPending}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
