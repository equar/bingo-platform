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
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { approveDeposit, deposits, rejectDeposit } from "../../api/admin";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

const statusOptions = ["PENDING", "APPROVED", "REJECTED"] as const;

export function DepositsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const normalizedQuery = query.trim();
  const depositsQuery = useQuery({
    queryKey: ["deposits", normalizedQuery, status, page, pageSize],
    queryFn: () => deposits(page, pageSize, normalizedQuery || undefined, status || undefined),
    placeholderData: keepPreviousData,
  });
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

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label="Search deposit, player, payment reference, or operation"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
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
          sx={{ minWidth: { xs: "100%", md: 220 } }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {statusOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {option}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {depositsQuery.isFetching && !depositsQuery.isLoading ? (
        <Alert severity="info">Updating deposit results...</Alert>
      ) : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label="deposit reviews table">
          <TableHead>
            <TableRow>
              <TableCell>Deposit / player</TableCell>
              <TableCell>Payment reference</TableCell>
              <TableCell align="right">Submitted cash</TableCell>
              <TableCell align="right">Credit</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Submitted</TableCell>
              <TableCell>Review note</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(depositsQuery.data?.content ?? []).map((deposit) => (
              <TableRow key={deposit.id} hover data-testid={`deposit-row-${deposit.id}`}>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography fontWeight={600}>Deposit #{deposit.id}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Player {deposit.playerId}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {deposit.operationId}
                    </Typography>
                  </Stack>
                </TableCell>
                <TableCell>{deposit.normalizedTelebirrReference}</TableCell>
                <TableCell align="right">
                  {deposit.submittedCashAmount} {deposit.currency}
                </TableCell>
                <TableCell align="right">{deposit.calculatedCreditAmount}</TableCell>
                <TableCell>
                  <Chip
                    label={deposit.status}
                    color={deposit.status === "PENDING" ? "warning" : "default"}
                    size="small"
                  />
                </TableCell>
                <TableCell>{new Date(deposit.submittedAt).toLocaleString()}</TableCell>
                <TableCell>{deposit.reviewNote ?? "n/a"}</TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button
                      data-testid={`deposit-approve-${deposit.id}`}
                      size="small"
                      variant="contained"
                      disabled={deposit.status !== "PENDING"}
                      onClick={() => {
                        setMode("APPROVE");
                        setSelectedId(deposit.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Approve
                    </Button>
                    <Button
                      data-testid={`deposit-reject-${deposit.id}`}
                      size="small"
                      variant="outlined"
                      disabled={deposit.status !== "PENDING"}
                      onClick={() => {
                        setMode("REJECT");
                        setSelectedId(deposit.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Reject
                    </Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {depositsQuery.data && depositsQuery.data.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8}>
                  <Alert severity="info">No deposits match the current search and filters.</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Box>

      <TablePagination
        component="div"
        count={depositsQuery.data?.totalElements ?? 0}
        page={page}
        onPageChange={(_, nextPage) => setPage(nextPage)}
        rowsPerPage={pageSize}
        onRowsPerPageChange={(event) => {
          setPageSize(Number(event.target.value));
          setPage(0);
        }}
        rowsPerPageOptions={[10, 20, 50]}
      />

      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{mode === "APPROVE" ? "Approve deposit" : "Reject deposit"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {actionMutation.isError ? (
              <Alert severity="error">{actionMutation.error.message}</Alert>
            ) : null}
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
