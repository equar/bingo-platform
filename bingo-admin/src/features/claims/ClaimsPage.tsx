import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { keepPreviousData } from "@tanstack/react-query";
import { approveWinClaim, rejectWinClaim, winClaims } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

const statusOptions = ["PENDING_REVIEW", "APPROVED", "REJECTED", "FAILED_SERVER_VALIDATION"] as const;
const patternOptions = ["LINE", "TWO_LINES", "FULL_HOUSE"] as const;

export function ClaimsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [pattern, setPattern] = useState("");
  const [validation, setValidation] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const normalizedQuery = query.trim();
  const claimsQuery = useQuery({
    queryKey: ["win-claims", normalizedQuery, status, pattern, validation, page, pageSize],
    queryFn: () =>
      winClaims(
        page,
        pageSize,
        normalizedQuery || undefined,
        status || undefined,
        pattern || undefined,
        validation === "" ? undefined : validation === "true",
      ),
    placeholderData: keepPreviousData,
  });
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

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label="Search claim, player, ticket, or session"
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
              {option.replace(/_/g, " ")}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Pattern"
          value={pattern}
          onChange={(event) => {
            setPattern(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 180 } }}
        >
          <MenuItem value="">All patterns</MenuItem>
          {patternOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {option.replace(/_/g, " ")}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Validation"
          value={validation}
          onChange={(event) => {
            setValidation(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 180 } }}
        >
          <MenuItem value="">All results</MenuItem>
          <MenuItem value="true">Passed</MenuItem>
          <MenuItem value="false">Failed</MenuItem>
        </TextField>
      </Stack>

      {claimsQuery.isFetching && !claimsQuery.isLoading ? <Alert severity="info">Updating claim results...</Alert> : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label="win claims table">
          <TableHead>
            <TableRow>
              <TableCell>Claim</TableCell>
              <TableCell>Player</TableCell>
              <TableCell>Ticket / session</TableCell>
              <TableCell>Pattern</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Validation</TableCell>
              <TableCell align="right">Draw seq.</TableCell>
              <TableCell>Submitted</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(claimsQuery.data?.content ?? []).map((claim) => (
              <TableRow key={claim.id} hover data-testid={`claim-row-${claim.id}`}>
                <TableCell>
                  <Stack spacing={0.5}>
                    <Typography fontWeight={600}>#{claim.id}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {claim.serverValidationCode ?? "No validation code"}
                    </Typography>
                  </Stack>
                </TableCell>
                <TableCell>{claim.claimantId}</TableCell>
                <TableCell>
                  <Typography variant="body2">Ticket {claim.ticketId}</Typography>
                  <Typography variant="caption" color="text.secondary">Session {claim.sessionId}</Typography>
                </TableCell>
                <TableCell>{claim.claimedPattern.replace(/_/g, " ")}</TableCell>
                <TableCell>
                  <Chip label={claim.status.replace(/_/g, " ")} size="small" />
                </TableCell>
                <TableCell>
                  <Chip
                    label={claim.serverValidationPassed ? "Passed" : "Failed"}
                    color={claim.serverValidationPassed ? "success" : "error"}
                    variant="outlined"
                    size="small"
                  />
                </TableCell>
                <TableCell align="right">{claim.drawSequenceSnapshot}</TableCell>
                <TableCell>{new Date(claim.createdAt).toLocaleString()}</TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button
                      data-testid={`claim-approve-${claim.id}`}
                      size="small"
                      variant="contained"
                      disabled={!claim.serverValidationPassed || claim.status !== "PENDING_REVIEW"}
                      onClick={() => {
                        setMode("APPROVE");
                        setSelectedId(claim.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Approve
                    </Button>
                    <Button
                      data-testid={`claim-reject-${claim.id}`}
                      size="small"
                      variant="outlined"
                      disabled={!claim.serverValidationPassed || claim.status !== "PENDING_REVIEW"}
                      onClick={() => {
                        setMode("REJECT");
                        setSelectedId(claim.id);
                        setConfirmOpen(true);
                      }}
                    >
                      Reject
                    </Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {claimsQuery.data && claimsQuery.data.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9}>
                  <Alert severity="info">No claims match the current search and filters.</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Box>

      <TablePagination
        component="div"
        count={claimsQuery.data?.totalElements ?? 0}
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
