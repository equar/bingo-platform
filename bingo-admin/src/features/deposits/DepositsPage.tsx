import { formatDualCalendarDateTime, formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { useTranslation } from "react-i18next";
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
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
} from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { useSession } from "../../api/session";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

const statusOptions = [
  "PENDING",
  "APPROVED",
  "REJECTED",
] as const;
const finalDepositStatuses = new Set(["APPROVED", "REJECTED"]);
const pendingDepositStatuses = new Set(["PENDING"]);

function displayedDepositStatus(status: string): string {
  return status === "APPROVED" || status === "REJECTED" ? status : "PENDING";
}

function maskPaymentReference(reference: string): string {
  const visibleSuffix = reference.slice(-4);
  return `${"*".repeat(Math.max(0, reference.length - 4))}${visibleSuffix}`;
}

function canVerifyDeposit(deposit: DepositClaimResponse, isAdmin: boolean, isSupport: boolean): boolean {
  return deposit.verifiedBy == null && (
    (isSupport && deposit.status === "PENDING") ||
    (isAdmin && deposit.status === "REJECTED")
  );
}

function canApproveDeposit(
  deposit: DepositClaimResponse,
  currentUserId: number | undefined,
  isAdmin: boolean,
  isSupport: boolean,
): boolean {
  const statusCanBeApproved = deposit.status === "PENDING" || (isAdmin && deposit.status === "REJECTED");
  return (isAdmin || isSupport)
    && statusCanBeApproved
    && deposit.verifiedBy != null
    && deposit.verifiedBy !== currentUserId;
}

export function DepositsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
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
  const idempotency = useIdempotencyKey();
  const isAdmin = authSession?.user.roles.includes("ADMIN") ?? false;
  const isSupport = authSession?.user.roles.includes("SUPPORT") ?? false;
  const canApproveDeposits = isAdmin || isSupport;
  const canRejectDeposits = isSupport;
  const displayPaymentReference = (reference: string) =>
    isSupport ? maskPaymentReference(reference) : reference;

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
                {option}
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
                      label={displayedDepositStatus(deposit.status)}
                      color={
                        pendingDepositStatuses.has(deposit.status)
                          ? "warning"
                          : deposit.status === "APPROVED"
                            ? "success"
                            : "default"
                      }
                      size="small"
                    />
                    {deposit.status === "PENDING" && deposit.verifiedBy == null ? (
                      <Typography
                        variant="caption"
                        display="block"
                        color="warning.main"
                      >
                        {t("Awaiting payment reference verification")}
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
                          {deposit.status}
                        </Button>
                      ) : null}
                      {canApproveDeposits &&
                      (deposit.status === "PENDING" || (isAdmin && deposit.status === "REJECTED")) ? (
                        <Tooltip
                          title={
                            deposit.verifiedBy == null
                              ? t(
                                  "A matching player-submitted payment reference is required before approval.",
                                )
                              : deposit.verifiedBy === authSession?.user.id
                                ? t("A different authorized staff member must approve this verified deposit.")
                                : deposit.status === "REJECTED" && !isAdmin
                                  ? t("An administrator must approve a verified rejected deposit.")
                                  : ""
                          }
                        >
                          <span>
                            <Button
                              data-testid={`deposit-approve-${deposit.id}`}
                              size="small"
                              variant="contained"
                              disabled={
                                !canApproveDeposit(deposit, authSession?.user.id, isAdmin, isSupport)
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
                      {canVerifyDeposit(deposit, isAdmin, isSupport) ? (
                        <Button
                          data-testid={`deposit-verify-${deposit.id}`}
                          size="small"
                          variant="outlined"
                          onClick={() => navigate(`/deposits/${deposit.id}`)}
                        >
                          {t("Verify deposit")}
                        </Button>
                      ) : null}
                      {canRejectDeposits && deposit.status === "PENDING" ? (
                        <Button
                          data-testid={`deposit-reject-${deposit.id}`}
                          size="small"
                          variant="outlined"
                          disabled={
                            deposit.status !== "PENDING"
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
                      ) : null}
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
                  [t("Status"), displayedDepositStatus(detailsDeposit.status)],
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
                    formatDualCalendarDateTime(detailsDeposit.submittedAt),
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
                      ? formatDualCalendarDateTime(detailsDeposit.verifiedAt)
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
                      ? formatDualCalendarDateTime(detailsDeposit.reviewedAt)
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
            {detailsDeposit && detailsDeposit.verifiedBy === authSession?.user.id ? (
              <Alert severity="info">
                {t("You verified this deposit. A different authorized staff member must approve it.")}
              </Alert>
            ) : detailsDeposit && detailsDeposit.verifiedBy == null ? (
              <Alert severity="info">
                {t("A matching payment reference must be verified before approval.")}
              </Alert>
            ) : null}
          </DialogContent>
          <DialogActions>
            {detailsDeposit &&
            finalDepositStatuses.has(detailsDeposit.status) ? (
              <Button disabled>
                {displayedDepositStatus(detailsDeposit.status)}
              </Button>
            ) : null}
            {detailsDeposit &&
            canApproveDeposits &&
            (detailsDeposit.status === "PENDING" || (isAdmin && detailsDeposit.status === "REJECTED")) ? (
              <>
                <Button
                  variant="contained"
                  disabled={
                    !canApproveDeposit(detailsDeposit, authSession?.user.id, isAdmin, isSupport)
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
                {canRejectDeposits && detailsDeposit.status === "PENDING" ? <Button
                  color="error"
                  disabled={
                    detailsDeposit.status !== "PENDING"
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
                </Button> : null}
              </>
            ) : null}
            {detailsDeposit &&
            canVerifyDeposit(detailsDeposit, isAdmin, isSupport) ? (
              <Button
                variant="contained"
                onClick={() => {
                  const depositId = detailsDeposit.id;
                  setDetailsDeposit(null);
                  navigate(`/deposits/${depositId}`);
                }}
              >
                {t("Verify deposit")}
              </Button>
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
      </Stack>
    </AdminTablePage>
  );
}
