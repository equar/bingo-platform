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
import { prizeEntitlements, settlePrizeEntitlement } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

export function PrizeEntitlementsPage() {
  const queryClient = useQueryClient();
  const entitlementsQuery = useQuery({ queryKey: ["prize-entitlements"], queryFn: () => prizeEntitlements(0, 50) });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [settlementReference, setSettlementReference] = useState("");
  const [settlementNote, setSettlementNote] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);

  const settleMutation = useMutation({
    mutationFn: async () => {
      if (selectedId == null) {
        throw new Error("Missing entitlement id");
      }
      return settlePrizeEntitlement(selectedId, {
        settlementReference: settlementReference.trim(),
        settlementNote: settlementNote.trim() || undefined,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["prize-entitlements"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setSelectedId(null);
      setSettlementReference("");
      setSettlementNote("");
      setConfirmOpen(false);
    },
  });

  if (entitlementsQuery.isLoading) {
    return <StatusState title="Loading prize entitlements" loading message="Fetching settlement queue" />;
  }

  if (entitlementsQuery.error) {
    return (
      <StatusState
        title="Unable to load prize entitlements"
        message={(entitlementsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => entitlementsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Prize entitlements</Typography>
        <Typography color="text.secondary">Settle approved claims into the winner wallet or external path.</Typography>
      </Box>

      {settleMutation.isError ? (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => settleMutation.reset()}>
              Dismiss
            </Button>
          }
        >
          {(settleMutation.error as Error)?.message ?? "Unable to settle entitlement."}
        </Alert>
      ) : null}

      {(entitlementsQuery.data?.content ?? []).length === 0 ? (
        <Alert severity="info">No prize entitlements are pending settlement.</Alert>
      ) : null}

      <Grid container spacing={2}>
        {(entitlementsQuery.data?.content ?? []).map((entitlement) => (
          <Grid item xs={12} md={6} key={entitlement.id}>
            <Card data-testid={`entitlement-card-${entitlement.id}`} sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardContent>
                <Stack spacing={1.25}>
                  <Box display="flex" justifyContent="space-between" gap={2} alignItems="start">
                    <div>
                      <Typography variant="h6">Entitlement #{entitlement.id}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Claim {entitlement.winClaimId} · Ticket {entitlement.ticketId} · Session {entitlement.sessionId}
                      </Typography>
                    </div>
                    <Chip label={entitlement.status} color="success" size="small" />
                  </Box>
                  <Typography>Prize type: {entitlement.prizeType}</Typography>
                  <Typography>Settlement path: {entitlement.settlementPath}</Typography>
                  <Typography>
                    Prize amount: {entitlement.prizeAmount} {entitlement.currency}
                  </Typography>
                  <Typography>Settlement reference: {entitlement.settlementReference ?? "n/a"}</Typography>
                  <Typography>Settlement note: {entitlement.settlementNote ?? "n/a"}</Typography>
                  <Stack direction="row" spacing={1}>
                    <Button
                      data-testid={`entitlement-settle-${entitlement.id}`}
                      variant="contained"
                      disabled={entitlement.status === "SETTLED"}
                      onClick={() => {
                        setSelectedId(entitlement.id);
                        setSettlementReference("");
                        setSettlementNote("");
                        setConfirmOpen(true);
                      }}
                    >
                      Settle
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Settle entitlement</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Settlement reference"
              value={settlementReference}
              onChange={(event) => setSettlementReference(event.target.value)}
              fullWidth
              required
            />
            <TextField
              label="Settlement note"
              value={settlementNote}
              onChange={(event) => setSettlementNote(event.target.value)}
              fullWidth
              multiline
              minRows={3}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => settleMutation.mutate()}
            disabled={settleMutation.isPending || settlementReference.trim().length === 0}
          >
            Confirm settlement
          </Button>
        </DialogActions>
      </Dialog>

    </Stack>
  );
}