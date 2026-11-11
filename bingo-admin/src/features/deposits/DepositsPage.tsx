import { useTranslation } from "react-i18next";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
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
  Tooltip,
  Typography,
} from "@mui/material";
import {
  approveDeposit,
  deposits,
  rejectDeposit,
  verifyDeposit,
} from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { translateAdminStatus } from "../../i18n";
import { useSession } from "../../api/session";
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
  const authSession = useSession();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("deposits");
  const [status, setStatus] = useListSessionState("deposits:status", () => statusOptions.find((option) => option === searchParams.get("status")) ?? "");
  const [page, setPage] = useListSessionState("deposits:page", 0);
  const [pageSize, setPageSize] = useListSessionState("deposits:size", 20);
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
  const [verificationDepositId, setVerificationDepositId] = useState<number | null>(null);
  const [statementEntryId, setStatementEntryId] = useState("");
  const [verificationOpen, setVerificationOpen] = useState(false);
  const idempotency = useIdempotencyKey();
  const canVerifyDeposits = authSession?.user.roles.includes("SUPPORT") ?? false;
  const canApproveDeposits = authSession?.user.roles.includes("ADMIN") ?? false;

  useEffect(() => {
    if (searchParams.has("status")) setStatus(statusOptions.find((option) => option === searchParams.get("status")) ?? "");
  }, [searchParams, setStatus]);

  const actionMutation = useMutation({
    mutationFn: async () => {
      if (selectedId == null) {
        throw new Error("Missing deposit id");
      }
      if (mode === "APPROVE") {
        return approveDeposit(selectedId, { reviewNote: comment.trim(), idempotencyKey: idempotency.getKey() });
      }
      return rejectDeposit(selectedId, { reviewNote: comment.trim(), idempotencyKey: idempotency.getKey() });
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
    mutationFn: () => {
      if (verificationDepositId == null || !Number.isSafeInteger(Number(statementEntryId)) || Number(statementEntryId) <= 0) {
        throw new Error("A valid bank statement entry ID is required");
      }
      return verifyDeposit(verificationDepositId, Number(statementEntryId));
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["deposits"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setVerificationOpen(false);
      setVerificationDepositId(null);
      setStatementEntryId("");
    },
  });
  if (depositsQuery.isLoading) {
    return <StatusState title={t("Loading deposits")} loading message={t("Fetching deposit queue")} />;
  }

  if (depositsQuery.error && !depositsQuery.data) {
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
    <AdminTablePage>
      <Stack spacing={3}>
      <AdminPageHeader title={t("Deposits")} description={t("Review player deposit requests.")} refresh={{ isFetching: depositsQuery.isFetching, failed: depositsQuery.isRefetchError, error: depositsQuery.error, onRefresh: () => depositsQuery.refetch() }} />

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
      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label={t("deposit reviews table")}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Deposit")}</TableCell>
              <TableCell>{t("Player")}</TableCell>
              <TableCell>{t("Phone number")}</TableCell>
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
                    <Typography variant="caption" color="text.secondary">
                      {deposit.operationId}
                    </Typography>
                  </Stack>
                </TableCell>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography fontWeight={600}>{deposit.playerDisplayName || `${t("Player")} #${deposit.playerId}`}</Typography>
                    <Typography variant="caption" color="text.secondary">{t("Player #")}{deposit.playerId}</Typography>
                  </Stack>
                </TableCell>
                <TableCell>{deposit.playerPhoneNumber ?? t("n/a")}</TableCell>
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
                  {["PENDING", "AWAITING_VERIFICATION", "VERIFICATION_EXCEPTION"].includes(deposit.status) ? (
                    <Typography variant="caption" display="block" color="warning.main">
                      {t("Awaiting independent bank verification")}
                    </Typography>
                  ) : null}
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
                    <Tooltip
                      title={deposit.status !== "VERIFIED"
                        ? t("A matched bank verification is required before approval.")
                        : !canApproveDeposits
                        ? t("An admin role is required to approve deposits.")
                          : deposit.verifiedBy === authSession?.user.id
                            ? t("A different admin must approve this verified deposit.")
                            : ""}
                    >
                      <span>
                        <Button
                          data-testid={`deposit-approve-${deposit.id}`}
                          size="small"
                          variant="contained"
                          disabled={
                            deposit.status !== "VERIFIED" ||
                            !canApproveDeposits ||
                            deposit.verifiedBy === authSession?.user.id
                          }
                          onClick={() => {
                            idempotency.resetKey();
                            actionMutation.reset();
                            setComment("");
                            setMode("APPROVE");
                            setSelectedId(deposit.id);
                            setConfirmOpen(true);
                          }}
                        >
                          {t("Approve")}
                        </Button>
                      </span>
                    </Tooltip>
                    {canVerifyDeposits && ["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION"].includes(deposit.status) ? (
                      <Button
                        data-testid={`deposit-verify-${deposit.id}`}
                        size="small"
                        variant="outlined"
                        onClick={() => {
                          verificationMutation.reset();
                          setVerificationDepositId(deposit.id);
                          setStatementEntryId("");
                          setVerificationOpen(true);
                        }}
                      >
                        {t("Verify")}
                      </Button>
                    ) : null}
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
                <TableCell colSpan={10}>
                  <Alert severity="info">{t("No deposits match the current search and filters.")}</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Box>

      <AdminTablePagination
        component="div"
        dataLoaded={!!depositsQuery.data}
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
              label={t("Review note")}
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
            disabled={!comment.trim() || actionMutation.isPending}
          >
            {actionOutcomeUnknown ? t("Retry same decision") : mode === "APPROVE" ? t("Approve") : t("Reject")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={verificationOpen}
        onClose={() => !verificationMutation.isPending && setVerificationOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{t("Verify deposit")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {verificationMutation.isError ? (
              <Alert severity="error">
                {t(getUserFacingErrorMessage(verificationMutation.error) ?? "Something went wrong. Please try again.")}
              </Alert>
            ) : null}
            <Typography variant="body2" color="text.secondary">
              {t("Match this deposit against an independent bank statement entry.")}
            </Typography>
            <TextField
              label={t("Bank statement entry ID")}
              type="number"
              value={statementEntryId}
              onChange={(event) => setStatementEntryId(event.target.value)}
              inputProps={{ min: 1, step: 1 }}
              fullWidth
              required
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVerificationOpen(false)} disabled={verificationMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => verificationMutation.mutate()}
            disabled={verificationMutation.isPending || !Number.isSafeInteger(Number(statementEntryId)) || Number(statementEntryId) <= 0}
          >
            {t("Verify")}
          </Button>
        </DialogActions>
      </Dialog>
      </Stack>
    </AdminTablePage>
  );
}
