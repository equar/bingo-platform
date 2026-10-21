import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { prizeEntitlements, settlePrizeEntitlement } from "../../api/admin";
import { StatusState } from "../../components/StatusState";
import { EntitlementReferenceDialog, type EntitlementReference } from "./EntitlementReferenceDialog";

export function PrizeEntitlementsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [settlementReference, setSettlementReference] = useState("");
  const [settlementNote, setSettlementNote] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reference, setReference] = useState<EntitlementReference | null>(null);
  const normalizedQuery = query.trim();
  const entitlementsQuery = useQuery({
    queryKey: ["prize-entitlements", normalizedQuery, status, page, pageSize],
    queryFn: () => prizeEntitlements(page, pageSize, normalizedQuery || undefined, status || undefined),
    placeholderData: keepPreviousData,
  });

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

  const entitlements = entitlementsQuery.data?.content ?? [];

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

      {entitlementsQuery.isFetching ? <Alert severity="info">Updating entitlement results...</Alert> : null}

      <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={2}
          sx={{ p: 2, borderBottom: 1, borderColor: "divider" }}
        >
          <TextField
            label="Search entitlements"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            placeholder="IDs, player, prize, operation, or reference"
            size="small"
            fullWidth
          />
          <TextField
            select
            label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
            size="small"
            sx={{ minWidth: { xs: "100%", md: 200 } }}
          >
            <MenuItem value="">All statuses</MenuItem>
            <MenuItem value="PENDING">Pending</MenuItem>
            <MenuItem value="SETTLED">Settled</MenuItem>
            <MenuItem value="VOID">Void</MenuItem>
          </TextField>
        </Stack>

        <TableContainer>
          <Table size="small" aria-label="prize entitlements table">
            <TableHead>
              <TableRow>
                <TableCell>Entitlement</TableCell>
                <TableCell>Player</TableCell>
                <TableCell>Claim</TableCell>
                <TableCell>Session</TableCell>
                <TableCell>Ticket</TableCell>
                <TableCell>Prize</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Created</TableCell>
                <TableCell>Settlement reference</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {entitlements.map((entitlement) => (
                <TableRow key={entitlement.id} hover data-testid={`entitlement-row-${entitlement.id}`}>
                  <TableCell>#{entitlement.id}</TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={`View player ${entitlement.claimantId} details`}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "player", id: entitlement.claimantId })}
                    >
                      #{entitlement.claimantId}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={`View claim ${entitlement.winClaimId} details`}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "claim", id: entitlement.winClaimId })}
                    >
                      #{entitlement.winClaimId}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={`View session ${entitlement.sessionId} details`}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "session", id: entitlement.sessionId })}
                    >
                      #{entitlement.sessionId}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={`View ticket ${entitlement.ticketId} details`}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "ticket", id: entitlement.ticketId })}
                    >
                      #{entitlement.ticketId}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography variant="body2" fontWeight={600}>
                        {entitlement.prizeAmount} {entitlement.currency}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {entitlement.prizeType} · {entitlement.settlementPath}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={entitlement.status}
                      color={
                        entitlement.status === "PENDING"
                          ? "warning"
                          : entitlement.status === "SETTLED"
                            ? "success"
                            : "default"
                      }
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{new Date(entitlement.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{entitlement.settlementReference ?? "Not settled"}</TableCell>
                  <TableCell align="right">
                    <Button
                      data-testid={`entitlement-settle-${entitlement.id}`}
                      variant="outlined"
                      size="small"
                      disabled={entitlement.status !== "PENDING"}
                      onClick={() => {
                        setSelectedId(entitlement.id);
                        setSettlementReference("");
                        setSettlementNote("");
                        setConfirmOpen(true);
                      }}
                    >
                      Settle
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {entitlementsQuery.data && entitlements.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10}>
                    <Alert severity="info">No prize entitlements match the current filters.</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={entitlementsQuery.data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => {
            setPageSize(Number(event.target.value));
            setPage(0);
          }}
          rowsPerPageOptions={[10, 20, 50]}
        />
      </Paper>

      <EntitlementReferenceDialog reference={reference} onClose={() => setReference(null)} />

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
