import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
  Link,
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
import { keepPreviousData } from "@tanstack/react-query";
import { approveWinClaim, rejectWinClaim, winClaims } from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { translateAdminStatus, translateAdminValue } from "../../i18n";
import { useIdempotencyKey } from "../common/useIdempotencyKey";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { EntitlementReferenceDialog, type EntitlementReference } from "../prizeEntitlements/EntitlementReferenceDialog";

const statusOptions = ["PENDING_REVIEW", "APPROVED", "REJECTED", "FAILED_SERVER_VALIDATION"] as const;
const patternOptions = [
  "LINE",
  "TWO_LINES",
  "FOUR_CORNERS",
  "POSTAGE_STAMP",
  "X_SHAPE",
  "T_SHAPE",
  "L_SHAPE",
  "OUTER_FRAME",
  "FULL_HOUSE",
] as const;

export function ClaimsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("claims");
  const [status, setStatus] = useListSessionState("claims:status", () => statusOptions.find((option) => option === searchParams.get("status")) ?? "");
  const [pattern, setPattern] = useListSessionState("claims:pattern", "");
  const [validation, setValidation] = useListSessionState("claims:validation", "");
  const [page, setPage] = useListSessionState("claims:page", 0);
  const [pageSize, setPageSize] = useListSessionState("claims:size", 20);
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
  const [reference, setReference] = useState<EntitlementReference | null>(null);
  const idempotency = useIdempotencyKey();

  useEffect(() => {
    if (searchParams.has("status")) setStatus(statusOptions.find((option) => option === searchParams.get("status")) ?? "");
  }, [searchParams, setStatus]);

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
    onError: (error) => {
      if (!parseApiError(error).outcomeUnknown) idempotency.resetKey();
    },
  });
  const actionOutcomeUnknown = actionMutation.isError && parseApiError(actionMutation.error).outcomeUnknown;

  if (claimsQuery.isLoading) {
    return <StatusState title={t("Loading claims")} loading message={t("Fetching win claims")} />;
  }

  if (claimsQuery.error) {
    return (
      <StatusState
        title={t("Unable to load claims")}
        message={t(getUserFacingErrorMessage(claimsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => claimsQuery.refetch()}
      />
    );
  }

  return (
    <AdminTablePage>
    <Stack spacing={3}>
      <AdminPageHeader title={t("Win claims")} description={t("Review submitted claims and record validation evidence.")} />

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label={t("Search claim, player, ticket, or session")}
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
        <TextField
          select
          label={t("Pattern")}
          value={pattern}
          onChange={(event) => {
            setPattern(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 180 } }}
        >
          <MenuItem value="">{t("All patterns")}</MenuItem>
          {patternOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {translateAdminValue(option, t)}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label={t("Validation")}
          value={validation}
          onChange={(event) => {
            setValidation(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 180 } }}
        >
          <MenuItem value="">{t("All results")}</MenuItem>
          <MenuItem value="true">{t("Passed")}</MenuItem>
          <MenuItem value="false">{t("Failed")}</MenuItem>
        </TextField>
      </Stack>

      {claimsQuery.isFetching && !claimsQuery.isLoading ? <Alert severity="info">{t("Updating claim results...")}</Alert> : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label={t("win claims table")}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Claim")}</TableCell>
              <TableCell>{t("Player")}</TableCell>
              <TableCell>{t("Ticket / session")}</TableCell>
              <TableCell>{t("Pattern")}</TableCell>
              <TableCell>{t("Status")}</TableCell>
              <TableCell>{t("Validation")}</TableCell>
              <TableCell align="right">{t("Draw seq.")}</TableCell>
              <TableCell>{t("Submitted")}</TableCell>
              <TableCell align="right">{t("Actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(claimsQuery.data?.content ?? []).map((claim) => (
              <TableRow key={claim.id} hover data-testid={`claim-row-${claim.id}`}>
                <TableCell>
                  <Stack spacing={0.5}>
                    <Typography fontWeight={600}>{t("#")}{claim.id}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {claim.serverValidationCode ?? t("No validation code")}
                    </Typography>
                  </Stack>
                </TableCell>
                <TableCell>
                  <Stack spacing={0.5}>
                    <Link
                      component="button"
                      type="button"
                      variant="body2"
                      underline="hover"
                      onClick={() => setReference({ kind: "player", id: claim.claimantId })}
                    >
                      {claim.claimantDisplayName || t("Player details unavailable")}
                    </Link>
                  </Stack>
                </TableCell>
                <TableCell>
                  <Stack spacing={0.5}>
                    <Link
                      component="button"
                      type="button"
                      variant="body2"
                      underline="hover"
                      onClick={() => setReference({ kind: "ticket", id: claim.ticketId })}
                    >
                      {t("Ticket")}{" "}{claim.publicTicketCode || t("Ticket number unavailable")}
                    </Link>
                    <Link
                      component="button"
                      type="button"
                      variant="caption"
                      underline="hover"
                      onClick={() => setReference({ kind: "session", id: claim.sessionId })}
                    >
                      {t("Session")}{" "}{claim.sessionId}
                    </Link>
                  </Stack>
                </TableCell>
                <TableCell>{translateAdminValue(claim.claimedPattern, t)}</TableCell>
                <TableCell>
                  <Chip label={translateAdminStatus(claim.status, t)} size="small" />
                </TableCell>
                <TableCell>
                  <Chip
                    label={claim.serverValidationPassed ? t("Passed") : t("Failed")}
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
                        idempotency.resetKey();
                        actionMutation.reset();
                        setReason("");
                        setMode("APPROVE");
                        setSelectedId(claim.id);
                        setConfirmOpen(true);
                      }}
                    >
                      {t("Approve")}</Button>
                    <Button
                      data-testid={`claim-reject-${claim.id}`}
                      size="small"
                      variant="outlined"
                      disabled={!claim.serverValidationPassed || claim.status !== "PENDING_REVIEW"}
                      onClick={() => {
                        idempotency.resetKey();
                        actionMutation.reset();
                        setReason("");
                        setMode("REJECT");
                        setSelectedId(claim.id);
                        setConfirmOpen(true);
                      }}
                    >
                      {t("Reject")}</Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {claimsQuery.data && claimsQuery.data.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9}>
                  <Alert severity="info">{t("No claims match the current search and filters.")}</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Box>

      <AdminTablePagination
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

      <Dialog open={confirmOpen} onClose={() => !actionOutcomeUnknown && !actionMutation.isPending && setConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{mode === "APPROVE" ? t("Approve claim") : t("Reject claim")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {actionMutation.isError ? (
              <Alert severity="error">{t(getUserFacingErrorMessage(actionMutation.error) ?? "Something went wrong. Please try again.")}</Alert>
            ) : null}
            <TextField
              label={mode === "APPROVE" ? t("Validation note") : t("Rejection reason")}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
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
            disabled={!reason.trim() || actionMutation.isPending}
          >
            {actionOutcomeUnknown ? t("Retry same decision") : t("Confirm")}
          </Button>
        </DialogActions>
      </Dialog>
      <EntitlementReferenceDialog reference={reference} onClose={() => setReference(null)} />
    </Stack>
    </AdminTablePage>
  );
}
