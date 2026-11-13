import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { useTranslation } from "react-i18next";
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
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
import type { DepositClaimResponse } from "../../api/admin";
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
const finalDepositStatuses = new Set(["APPROVED", "REJECTED"]);

function maskPaymentReference(reference: string): string {
  const visibleSuffix = reference.slice(-4);
  return `${"*".repeat(Math.max(0, reference.length - 4))}${visibleSuffix}`;
}

export function DepositsPage() {
  const { t } = useTranslation();
  const authSession = useSession();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("deposits");
  const [status, setStatus] = useListSessionState(
    "deposits:status",
    () =>
      statusOptions.find((option) => option === searchParams.get("status")) ??
      "",
  );
  const [page, setPage] = useListSessionState("deposits:page", 0);
  const [pageSize, setPageSize] = useListSessionState("deposits:size", 20);
  const normalizedQuery = query.trim();
  const depositsQuery = useQuery({
    queryKey: ["deposits", normalizedQuery, status, page, pageSize],
    queryFn: () =>
      deposits(
        page,
        pageSize,
        normalizedQuery || undefined,
        status || undefined,
      ),
    placeholderData: keepPreviousData,
  });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detailsDeposit, setDetailsDeposit] =
    useState<DepositClaimResponse | null>(null);
  const [comment, setComment] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [mode, setMode] = useState<"APPROVE" | "REJECT">("APPROVE");
  const [verificationDepositId, setVerificationDepositId] = useState<
    number | null
  >(null);
  const [paymentReference, setPaymentReference] = useState("");
  const [verificationOpen, setVerificationOpen] = useState(false);
  const idempotency = useIdempotencyKey();
  const isAdmin = authSession?.user.roles.includes("ADMIN") ?? false;
  const canApproveDeposits = isAdmin;
  const canVerifyDeposits =
    !isAdmin && (authSession?.user.roles.includes("SUPPORT") ?? false);
  const canRejectDeposits = canApproveDeposits || canVerifyDeposits;
  const displayPaymentReference = (reference: string) =>
    canVerifyDeposits ? maskPaymentReference(reference) : reference;

  useEffect(() => {
    if (searchParams.has("status"))
      setStatus(
        statusOptions.find((option) => option === searchParams.get("status")) ??
          "",
      );
  }, [searchParams, setStatus]);

  const actionMutation = useMutation({
    mutationFn: async () => {
      if (selectedId == null) {
        throw new Error("Missing deposit id");
      }
      if (mode === "APPROVE") {
        return approveDeposit(selectedId, {
          reviewNote: comment.trim(),
          idempotencyKey: idempotency.getKey(),
        });
      }
      return rejectDeposit(selectedId, {
        reviewNote: comment.trim(),
        idempotencyKey: idempotency.getKey(),
      });
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
  const actionOutcomeUnknown =
    actionMutation.isError &&
    parseApiError(actionMutation.error).outcomeUnknown;
  const verificationMutation = useMutation({
    mutationFn: () => {
      const reference = paymentReference.trim();
      if (
        verificationDepositId == null ||
        reference.length === 0 ||
        reference.length > 120
      ) {
        throw new Error("A valid bank statement payment reference is required");
      }
      return verifyDeposit(verificationDepositId, reference);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["deposits"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      setVerificationOpen(false);
      setVerificationDepositId(null);
      setPaymentReference("");
    },
  });
  if (depositsQuery.isLoading) {
    return (
      <StatusState
        title={t("Loading deposits")}
        loading
        message={t("Fetching deposit queue")}
      />
    );
  }

  if (depositsQuery.error && !depositsQuery.data) {
    return (
      <StatusState
        title={t("Unable to load deposits")}
        message={t(
          getUserFacingErrorMessage(depositsQuery.error) ??
            "Something went wrong. Please try again.",
        )}
        actionLabel={t("Retry")}
        onAction={() => depositsQuery.refetch()}
      />
    );
  }

  return (
    <AdminTablePage>
      <Stack spacing={3}>
        <AdminPageHeader
          title={t("Deposits")}
          description={t("Review player deposit requests.")}
          refresh={{
            isFetching: depositsQuery.isFetching,
            failed: depositsQuery.isRefetchError,
            error: depositsQuery.error,
            onRefresh: () => depositsQuery.refetch(),
          }}
        />

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
                <TableRow
                  key={deposit.id}
                  hover
                  data-testid={`deposit-row-${deposit.id}`}
                >
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Button
                        data-testid={`deposit-details-${deposit.id}`}
                        aria-label={`${t("View details for deposit")} #${deposit.id}`}
                        onClick={() => setDetailsDeposit(deposit)}
                        sx={{
                          alignSelf: "flex-start",
                          minWidth: 0,
                          p: 0,
                          fontWeight: 600,
                          textTransform: "none",
                        }}
                      >
                        {t("Deposit #")}
                        {deposit.id}
                      </Button>
                      <Typography variant="caption" color="text.secondary">
                        {deposit.operationId}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography fontWeight={600}>
                        {deposit.playerDisplayName ||
                          `${t("Player")} #${deposit.playerId}`}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {t("Player #")}
                        {deposit.playerId}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{deposit.playerPhoneNumber ?? t("n/a")}</TableCell>
                  <TableCell>
                    {displayPaymentReference(
                      deposit.normalizedTelebirrReference,
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {deposit.submittedCashAmount} {deposit.currency}
                  </TableCell>
                  <TableCell align="right">
                    {deposit.calculatedCreditAmount}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={translateAdminStatus(deposit.status, t)}
                      color={
                        deposit.status === "PENDING" ||
                        deposit.status === "VERIFICATION_EXCEPTION"
                          ? "warning"
                          : deposit.status === "VERIFIED"
                            ? "success"
                            : "default"
                      }
                      size="small"
                    />
                    {[
                      "PENDING",
                      "AWAITING_VERIFICATION",
                      "VERIFICATION_EXCEPTION",
                    ].includes(deposit.status) ? (
                      <Typography
                        variant="caption"
                        display="block"
                        color="warning.main"
                      >
                        {t("Awaiting independent bank verification")}
                      </Typography>
                    ) : null}
                    {deposit.verifiedBy != null ? (
                      <Typography
                        variant="caption"
                        display="block"
                        color="text.secondary"
                      >
                        {t("Verified by user #")}
                        {deposit.verifiedBy}
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    {formatEthiopianDateTime(deposit.submittedAt)}
                  </TableCell>
                  <TableCell>{deposit.reviewNote ?? t("n/a")}</TableCell>
                  <TableCell align="right">
                    <Stack
                      direction="row"
                      spacing={1}
                      justifyContent="flex-end"
                    >
                      {finalDepositStatuses.has(deposit.status) ? (
                        <Button size="small" disabled>
                          {translateAdminStatus(deposit.status, t)}
                        </Button>
                      ) : null}
                      {canApproveDeposits &&
                      !finalDepositStatuses.has(deposit.status) ? (
                        <Tooltip
                          title={
                            deposit.status !== "VERIFIED"
                              ? t(
                                  "A matched bank verification is required before approval.",
                                )
                              : !canApproveDeposits
                                ? t(
                                    "An admin role is required to approve deposits.",
                                  )
                                : deposit.verifiedBy === authSession?.user.id
                                  ? t(
                                      "A different admin must approve this verified deposit.",
                                    )
                                  : ""
                          }
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
                      ) : null}
                      {canVerifyDeposits &&
                      [
                        "PENDING",
                        "AWAITING_VERIFICATION",
                        "VERIFIED",
                        "VERIFICATION_EXCEPTION",
                      ].includes(deposit.status) ? (
                        <Button
                          data-testid={`deposit-verify-${deposit.id}`}
                          size="small"
                          variant="outlined"
                          onClick={() => {
                            verificationMutation.reset();
                            setVerificationDepositId(deposit.id);
                            setPaymentReference("");
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
                        sx={{
                          display:
                            finalDepositStatuses.has(deposit.status) ||
                            !canRejectDeposits
                              ? "none"
                              : undefined,
                        }}
                        disabled={
                          ![
                            "PENDING",
                            "AWAITING_VERIFICATION",
                            "VERIFIED",
                            "VERIFICATION_EXCEPTION",
                          ].includes(deposit.status)
                        }
                        onClick={() => {
                          idempotency.resetKey();
                          actionMutation.reset();
                          setComment("");
                          setMode("REJECT");
                          setSelectedId(deposit.id);
                          setConfirmOpen(true);
                        }}
                      >
                        {t("Reject")}
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
              {depositsQuery.data && depositsQuery.data.content.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10}>
                    <Alert severity="info">
                      {t("No deposits match the current search and filters.")}
                    </Alert>
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

        <Dialog
          open={detailsDeposit != null}
          onClose={() => setDetailsDeposit(null)}
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>
            {detailsDeposit
              ? `${t("Deposit details")} #${detailsDeposit.id}`
              : t("Deposit details")}
          </DialogTitle>
          <DialogContent>
            {detailsDeposit ? (
              <Stack spacing={1.5} sx={{ pt: 1 }}>
                {[
                  [t("Status"), translateAdminStatus(detailsDeposit.status, t)],
                  [t("Operation ID"), detailsDeposit.operationId],
                  [
                    t("Player"),
                    `${detailsDeposit.playerDisplayName || `${t("Player")} #${detailsDeposit.playerId}`} (#${detailsDeposit.playerId})`,
                  ],
                  [
                    t("Phone number"),
                    detailsDeposit.playerPhoneNumber ?? t("n/a"),
                  ],
                  [
                    t("Payment reference"),
                    displayPaymentReference(
                      detailsDeposit.normalizedTelebirrReference,
                    ),
                  ],
                  [
                    t("Submitted cash"),
                    `${detailsDeposit.submittedCashAmount} ${detailsDeposit.currency}`,
                  ],
                  [t("Conversion rate"), detailsDeposit.conversionRateSnapshot],
                  [
                    t("Credit"),
                    `${detailsDeposit.calculatedCreditAmount} ${detailsDeposit.currency}`,
                  ],
                  [
                    t("Submitted"),
                    formatEthiopianDateTime(detailsDeposit.submittedAt),
                  ],
                  [
                    t("Verified by user #"),
                    detailsDeposit.verifiedBy == null
                      ? t("n/a")
                      : String(detailsDeposit.verifiedBy),
                  ],
                  [
                    t("Verified at"),
                    detailsDeposit.verifiedAt
                      ? formatEthiopianDateTime(detailsDeposit.verifiedAt)
                      : t("n/a"),
                  ],
                  [
                    t("Reviewed by"),
                    detailsDeposit.reviewedBy == null
                      ? t("n/a")
                      : String(detailsDeposit.reviewedBy),
                  ],
                  [
                    t("Reviewed at"),
                    detailsDeposit.reviewedAt
                      ? formatEthiopianDateTime(detailsDeposit.reviewedAt)
                      : t("n/a"),
                  ],
                  [t("Review note"), detailsDeposit.reviewNote ?? t("n/a")],
                  [
                    t("Payment instructions"),
                    detailsDeposit.paymentInstructions?.paymentInstructions ??
                      t("n/a"),
                  ],
                ].map(([label, value]) => (
                  <Box key={label}>
                    <Typography variant="caption" color="text.secondary">
                      {label}
                    </Typography>
                    <Typography sx={{ overflowWrap: "anywhere" }}>
                      {value}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            ) : null}
          </DialogContent>
          <DialogActions>
            {detailsDeposit &&
            finalDepositStatuses.has(detailsDeposit.status) ? (
              <Button disabled>
                {translateAdminStatus(detailsDeposit.status, t)}
              </Button>
            ) : null}
            {detailsDeposit &&
            canApproveDeposits &&
            !finalDepositStatuses.has(detailsDeposit.status) ? (
              <>
                <Button
                  variant="contained"
                  disabled={
                    detailsDeposit.status !== "VERIFIED" ||
                    detailsDeposit.verifiedBy === authSession?.user.id
                  }
                  onClick={() => {
                    idempotency.resetKey();
                    actionMutation.reset();
                    setComment("");
                    setMode("APPROVE");
                    setSelectedId(detailsDeposit.id);
                    setDetailsDeposit(null);
                    setConfirmOpen(true);
                  }}
                >
                  {t("Approve")}
                </Button>
                <Button
                  color="error"
                  disabled={
                    ![
                      "PENDING",
                      "AWAITING_VERIFICATION",
                      "VERIFIED",
                      "VERIFICATION_EXCEPTION",
                    ].includes(detailsDeposit.status)
                  }
                  onClick={() => {
                    idempotency.resetKey();
                    actionMutation.reset();
                    setComment("");
                    setMode("REJECT");
                    setSelectedId(detailsDeposit.id);
                    setDetailsDeposit(null);
                    setConfirmOpen(true);
                  }}
                >
                  {t("Reject")}
                </Button>
              </>
            ) : null}
            {detailsDeposit &&
            !canApproveDeposits &&
            canVerifyDeposits &&
            !finalDepositStatuses.has(detailsDeposit.status) ? (
              <>
                <Button
                  variant="contained"
                  disabled={
                    ![
                      "PENDING",
                      "AWAITING_VERIFICATION",
                      "VERIFIED",
                      "VERIFICATION_EXCEPTION",
                    ].includes(detailsDeposit.status)
                  }
                  onClick={() => {
                    verificationMutation.reset();
                    setVerificationDepositId(detailsDeposit.id);
                    setPaymentReference("");
                    setDetailsDeposit(null);
                    setVerificationOpen(true);
                  }}
                >
                  {t("Verify")}
                </Button>
                <Button
                  color="error"
                  disabled={
                    ![
                      "PENDING",
                      "AWAITING_VERIFICATION",
                      "VERIFIED",
                      "VERIFICATION_EXCEPTION",
                    ].includes(detailsDeposit.status)
                  }
                  onClick={() => {
                    idempotency.resetKey();
                    actionMutation.reset();
                    setComment("");
                    setMode("REJECT");
                    setSelectedId(detailsDeposit.id);
                    setDetailsDeposit(null);
                    setConfirmOpen(true);
                  }}
                >
                  {t("Reject")}
                </Button>
              </>
            ) : null}
            <Button onClick={() => setDetailsDeposit(null)}>
              {t("Close")}
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog
          open={confirmOpen}
          onClose={() =>
            !actionOutcomeUnknown &&
            !actionMutation.isPending &&
            setConfirmOpen(false)
          }
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>
            {mode === "APPROVE" ? t("Approve deposit") : t("Reject deposit")}
          </DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              {actionMutation.isError ? (
                <Alert severity="error">
                  {t(
                    getUserFacingErrorMessage(actionMutation.error) ??
                      "Something went wrong. Please try again.",
                  )}
                </Alert>
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
            <Button
              onClick={() => setConfirmOpen(false)}
              disabled={actionOutcomeUnknown || actionMutation.isPending}
            >
              {t("Cancel")}
            </Button>
            <Button
              variant="contained"
              onClick={() => actionMutation.mutate()}
              disabled={!comment.trim() || actionMutation.isPending}
            >
              {actionOutcomeUnknown
                ? t("Retry same decision")
                : mode === "APPROVE"
                  ? t("Approve")
                  : t("Reject")}
            </Button>
          </DialogActions>
        </Dialog>
        <Dialog
          open={verificationOpen}
          onClose={() =>
            !verificationMutation.isPending && setVerificationOpen(false)
          }
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>{t("Verify deposit")}</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              {verificationMutation.isError ? (
                <Alert severity="error">
                  {t(
                    getUserFacingErrorMessage(verificationMutation.error) ??
                      "Something went wrong. Please try again.",
                  )}
                </Alert>
              ) : null}
              <Typography variant="body2" color="text.secondary">
                {t(
                  "Match this deposit against an independent bank statement entry using its transaction reference.",
                )}
              </Typography>
              <TextField
                label={t("Bank statement payment reference")}
                value={paymentReference}
                onChange={(event) => setPaymentReference(event.target.value)}
                inputProps={{ maxLength: 120 }}
                fullWidth
                required
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={() => setVerificationOpen(false)}
              disabled={verificationMutation.isPending}
            >
              {t("Cancel")}
            </Button>
            <Button
              variant="contained"
              onClick={() => verificationMutation.mutate()}
              disabled={
                verificationMutation.isPending ||
                paymentReference.trim().length === 0 ||
                paymentReference.trim().length > 120
              }
            >
              {t("Verify")}
            </Button>
          </DialogActions>
        </Dialog>
      </Stack>
    </AdminTablePage>
  );
}
