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
import { approveWinClaim, rejectWinClaim, winClaims } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

export function ClaimsPage() {
  const queryClient = useQueryClient();
  const claimsQuery = useQuery({ queryKey: ["win-claims"], queryFn: () => winClaims(0, 50) });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [mode, setMode] = useState<"APPROVE" | "REJECT">("APPROVE");
  const idempotency = useIdempotencyKey();

  const actionMutation = useMutation({
    mutationFn: async () => {
      if (selectedId == null) {
        throw new Error("Missing claim id");
      }
      if (mode === "APPROVE") {
        return approveWinClaim(selectedId, { reviewNote: reason, idempotencyKey: idempotency.getKey() });
      }
      return rejectWinClaim(selectedId, { reviewNote: reason, idempotencyKey: idempotency.getKey() });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["win-claims"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      idempotency.resetKey();
      setConfirmOpen(false);
      setSelectedId(null);
      setReason("");
    },
  });

  if (claimsQuery.isLoading) {
    return <StatusState title="Loading claims" loading message="Fetching win claims" />;
  }

  if (claimsQuery.error) {
    return (
      <StatusState
        title="Unable to load claims"
        message={(claimsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => claimsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Win claims</Typography>
        <Typography color="text.secondary">Review submitted claims and record validation evidence.</Typography>
      </Box>

      {(claimsQuery.data?.content ?? []).length === 0 ? (
        <Alert severity="info">No win claims awaiting review.</Alert>
      ) : null}

      <Grid container spacing={2}>
        {(claimsQuery.data?.content ?? []).map((claim) => (
          <Grid item xs={12} md={6} key={claim.id}>
            <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardContent>
                <Stack spacing={1.25}>
                  <Box display="flex" justifyContent="space-between" gap={2}>
                    <div>
                      <Typography variant="h6">Claim #{claim.id}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Ticket {claim.ticketId} · Session {claim.sessionId}
                      </Typography>
                    </div>
                    <Chip label={claim.status} color="warning" size="small" />
                  </Box>
                  <Typography>Pattern: {claim.claimedPattern}</Typography>
                  <Typography>Validation: {claim.serverValidationPassed ? "Passed" : "Pending / failed"}</Typography>
                  <Typography>Draw sequence snapshot: {claim.drawSequenceSnapshot}</Typography>
                  <Typography variant="body2">Evidence: {claim.evidenceJson ?? "n/a"}</Typography>
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="contained"
                      onClick={() => {
                        setMode("APPROVE");
                        setSelectedId(claim.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Approve
                    </Button>
                    <Button
                      variant="outlined"
                      onClick={() => {
                        setMode("REJECT");
                        setSelectedId(claim.id);
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
        <DialogTitle>{mode === "APPROVE" ? "Approve claim" : "Reject claim"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label={mode === "APPROVE" ? "Validation note" : "Rejection reason"}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
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
            disabled={!reason || actionMutation.isPending}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
