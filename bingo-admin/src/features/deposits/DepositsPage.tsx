import { useTranslation } from "react-i18next";
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
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import {
  approveDeposit,
  deposits,
  rejectDeposit,
  verifyDeposit,
  type DepositVerificationResponse,
} from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { translateAdminStatus } from "../../i18n";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

const statusOptions = [
  "PENDING",
  "AWAITING_VERIFICATION",
  "VERIFIED",
  "VERIFICATION_EXCEPTION",
  "APPROVED",
  "REJECTED",
] as const;

export function DepositsPage() {
  const { t } = useTranslation();
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
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [statementEntryId, setStatementEntryId] = useState("");
  const [lastVerification, setLastVerification] = useState<DepositVerificationResponse | null>(null);
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
    onError: (error) => {
      if (!parseApiError(error).outcomeUnknown) idempotency.resetKey();
    },
  });
  const actionOutcomeUnknown = actionMutation.isError && parseApiError(actionMutation.error).outcomeUnknown;
  const verificationMutation = useMutation({
    mutationFn: async () => {
      if (selectedId == null || !/^[1-9]\d*$/.test(statementEntryId)) {
        throw new Error("Enter a valid bank statement entry ID");
      }
      return verifyDeposit(selectedId, Number(statementEntryId));
    },
    onSuccess: async (verification) => {
      setLastVerification(verification);
      await queryClient.invalidateQueries({ queryKey: ["deposits"] });
      setVerifyOpen(false);
      setSelectedId(null);
      setStatementEntryId("");
    },
  });

  if (depositsQuery.isLoading) {
    return <StatusState title={t("Loading deposits")} loading message={t("Fetching deposit queue")} />;
  }

  if (depositsQuery.error) {
    return (
      <StatusState
        title={t("Unable to load deposits")}
        message={t(getUserFacingErrorMessage(depositsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => depositsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">{t("Deposits")}</Typography>
        <Typography color="text.secondary">{t("Verify payment references, snapshots, and review history.")}</Typography>
      </Box>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label={t("Search deposit, player, payment reference, or operation")}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          fullWidth
        />
        <TextField
          select
          label={t("Status")}
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 220 } }}
        >
          <MenuItem value="">{t("All statuses")}</MenuItem>
          {statusOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {translateAdminStatus(option, t)}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {depositsQuery.isFetching && !depositsQuery.isLoading ? (
        <Alert severity="info">{t("Updating deposit results...")}</Alert>
      ) : null}
      {lastVerification ? (
        <Alert
          severity={lastVerification.outcome === "MATCHED" ? "success" : "warning"}
          onClose={() => setLastVerification(null)}
        >
          {t("Bank evidence #")}{lastVerification.statementEntryId} {t("outcome:")}{" "}{lastVerification.outcome}{t(". Account #")}{lastVerification.receivingBankAccountId ?? t("Unknown")}{t("Reference:")}{" "}{lastVerification.transactionReference ?? t("Unavailable")}{t(";")}{" "}{lastVerification.amount ?? t("Unknown")} {lastVerification.currency ?? ""}{t("Date")}{t(":")}{" "}
          {lastVerification.transactionDate ?? t("Unknown")}{t(";")}{" "}{lastVerification.synthetic ? t("synthetic QA statement") : lastVerification.evidenceSource}{t(".")}</Alert>
      ) : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label={t("deposit reviews table")}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Deposit / player")}</TableCell>
              <TableCell>{t("Payment reference")}</TableCell>
              <TableCell align="right">{t("Submitted cash")}</TableCell>
              <TableCell align="right">{t("Credit")}</TableCell>
              <TableCell>{t("Status")}</TableCell>
              <TableCell>{t("Submitted")}</TableCell>
              <TableCell>{t("Review note")}</TableCell>
              <TableCell align="right">{t("Actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(depositsQuery.data?.content ?? []).map((deposit) => (
              <TableRow key={deposit.id} hover data-testid={`deposit-row-${deposit.id}`}>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography fontWeight={600}>{t("Deposit #")}{deposit.id}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {t("Player")}{" "}{deposit.playerId}
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
                    label={translateAdminStatus(deposit.status, t)}
                  color={deposit.status === "PENDING" || deposit.status === "VERIFICATION_EXCEPTION"
                    ? "warning"
                    : deposit.status === "VERIFIED" ? "success" : "default"}
                  size="small"
                  />
                  {deposit.verifiedBy != null ? (
                  <Typography variant="caption" display="block" color="text.secondary">
                    {t("Verified by user #")}{deposit.verifiedBy}
                  </Typography>
                  ) : null}
                </TableCell>
                <TableCell>{new Date(deposit.submittedAt).toLocaleString()}</TableCell>
                <TableCell>{deposit.reviewNote ?? t("n/a")}</TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button
                      data-testid={`deposit-verify-${deposit.id}`}
                      size="small"
                      variant="outlined"
                      disabled={!["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION"].includes(deposit.status)}
                      onClick={() => {
                        setSelectedId(deposit.id);
                        setStatementEntryId("");
                        setLastVerification(null);
                        verificationMutation.reset();
                        setVerifyOpen(true);
                      }}
                    >
                      {t("Verify")}</Button>
                    <Button
                      data-testid={`deposit-approve-${deposit.id}`}
                      size="small"
                      variant="contained"
                      disabled={deposit.status !== "VERIFIED"}
                      onClick={() => {
                        idempotency.resetKey();
                        actionMutation.reset();
                        setComment("");
                        setMode("APPROVE");
                        setSelectedId(deposit.id);
                        setConfirmOpen(true);
                      }}
                    >
                      {t("Approve")}</Button>
                    <Button
                      data-testid={`deposit-reject-${deposit.id}`}
                      size="small"
                      variant="outlined"
                      disabled={!["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION"].includes(deposit.status)}
                      onClick={() => {
                        idempotency.resetKey();
                        actionMutation.reset();
                        setComment("");
                        setMode("REJECT");
                        setSelectedId(deposit.id);
                        setConfirmOpen(true);
                      }}
                    >
                      {t("Reject")}</Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {depositsQuery.data && depositsQuery.data.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8}>
                  <Alert severity="info">{t("No deposits match the current search and filters.")}</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Box>

      <AdminTablePagination
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

      <Dialog open={confirmOpen} onClose={() => !actionOutcomeUnknown && !actionMutation.isPending && setConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{mode === "APPROVE" ? t("Approve deposit") : t("Reject deposit")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {actionMutation.isError ? (
              <Alert severity="error">{t(getUserFacingErrorMessage(actionMutation.error) ?? "Something went wrong. Please try again.")}</Alert>
            ) : null}
            <TextField
              label={mode === "APPROVE" ? t("Review note") : t("Rejection reason")}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              disabled={actionOutcomeUnknown}
              fullWidth
              multiline
              minRows={3}
              required
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={actionOutcomeUnknown || actionMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => actionMutation.mutate()}
            disabled={!comment || actionMutation.isPending}
          >
            {actionOutcomeUnknown ? t("Retry same decision") : t("Confirm")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={verifyOpen} onClose={() => !verificationMutation.isPending && setVerifyOpen(false)}
        fullWidth maxWidth="sm">
        <DialogTitle>{t("Independently verify bank payment")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning">
              {t("A player receipt or review note is not payment evidence. Use an independently reconciled bank statement entry.")}</Alert>
            {verificationMutation.isError ? (
              <Alert severity="error">{t(getUserFacingErrorMessage(verificationMutation.error) ?? "Something went wrong. Please try again.")}</Alert>
            ) : null}
            <TextField
              label={t("Bank statement entry ID")}
              type="number"
              value={statementEntryId}
              onChange={(event) => setStatementEntryId(event.target.value)}
              inputProps={{ min: 1, step: 1 }}
              required
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVerifyOpen(false)} disabled={verificationMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => verificationMutation.mutate()}
            disabled={!/^[1-9]\d*$/.test(statementEntryId) || verificationMutation.isPending}
          >
            {t("Verify payment")}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
