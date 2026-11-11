import { useTranslation } from "react-i18next";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
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
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { prizeEntitlements, settlePrizeEntitlement } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { translateAdminStatus, translateAdminValue } from "../../i18n";
import { EntitlementReferenceDialog, type EntitlementReference } from "./EntitlementReferenceDialog";

export function PrizeEntitlementsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [query, setQuery] = usePrivateListSearch("prize-entitlements");
  const [status, setStatus] = useListSessionState("prize-entitlements:status", "");
  const [page, setPage] = useListSessionState("prize-entitlements:page", 0);
  const [pageSize, setPageSize] = useListSessionState("prize-entitlements:size", 20);
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
    return <StatusState title={t("Loading prize entitlements")} loading message={t("Fetching settlement queue")} />;
  }

  if (entitlementsQuery.error) {
    return (
      <StatusState
        title={t("Unable to load prize entitlements")}
        message={t(getUserFacingErrorMessage(entitlementsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => entitlementsQuery.refetch()}
      />
    );
  }

  const entitlements = entitlementsQuery.data?.content ?? [];

  return (
    <AdminTablePage>
    <Stack spacing={3}>
      <AdminPageHeader title={t("Prize entitlements")} description={t("Review approved claims and their prize entitlement details.")} />

      <Alert severity="warning">
        {t("Prize settlement is blocked until payout evidence, accounting treatment, and reconciliation controls are approved.")}</Alert>

      {entitlementsQuery.isFetching ? <Alert severity="info">{t("Updating entitlement results...")}</Alert> : null}

      <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={2}
          sx={{ p: 2, borderBottom: 1, borderColor: "divider" }}
        >
          <TextField
            label={t("Search entitlements")}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            placeholder={t("IDs, player, prize, operation, or reference")}
            size="small"
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
            size="small"
            sx={{ minWidth: { xs: "100%", md: 200 } }}
          >
            <MenuItem value="">{t("All statuses")}</MenuItem>
            <MenuItem value="PENDING">{t("Pending")}</MenuItem>
            <MenuItem value="SETTLED">{t("Settled")}</MenuItem>
            <MenuItem value="VOID">{t("Void")}</MenuItem>
          </TextField>
        </Stack>

        <TableContainer>
          <Table size="small" aria-label={t("prize entitlements table")}>
            <TableHead>
              <TableRow>
                <TableCell>{t("Entitlement")}</TableCell>
                <TableCell>{t("Player")}</TableCell>
                <TableCell>{t("Claim")}</TableCell>
                <TableCell>{t("Session")}</TableCell>
                <TableCell>{t("Ticket")}</TableCell>
                <TableCell>{t("Prize")}</TableCell>
                <TableCell>{t("Status")}</TableCell>
                <TableCell>{t("Created")}</TableCell>
                <TableCell>{t("Settlement reference")}</TableCell>
                <TableCell align="right">{t("Action")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {entitlements.map((entitlement) => (
                <TableRow key={entitlement.id} hover data-testid={`entitlement-row-${entitlement.id}`}>
                  <TableCell>{t("#")}{entitlement.id}</TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={t("View player {{id}} details", { id: entitlement.claimantId })}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "player", id: entitlement.claimantId })}
                    >
                      {entitlement.claimantDisplayName || t("Player details unavailable")}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={t("View claim {{id}} details", { id: entitlement.winClaimId })}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "claim", id: entitlement.winClaimId })}
                    >
                      {entitlement.claimedPattern
                        ? `${translateAdminValue(entitlement.claimedPattern, t)} · ${translateAdminStatus(entitlement.claimStatus ?? "", t)}`
                        : t("Claim details")}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={t("View session {{id}} details", { id: entitlement.sessionId })}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "session", id: entitlement.sessionId })}
                    >
                      {entitlement.sessionName || t("Session details")}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      aria-label={t("View ticket {{id}} details", { id: entitlement.ticketId })}
                      sx={{ minWidth: 0, p: 0 }}
                      onClick={() => setReference({ kind: "ticket", id: entitlement.ticketId })}
                    >
                      {entitlement.publicTicketCode || t("Ticket details")}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography variant="body2" fontWeight={600}>
                        {entitlement.prizeAmount} {entitlement.currency}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {translateAdminValue(entitlement.prizeType, t)} {t("·")}{" "}
                        {translateAdminValue(entitlement.settlementPath, t)}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={translateAdminStatus(entitlement.status, t)}
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
                  <TableCell>{entitlement.settlementReference ?? t("Not settled")}</TableCell>
                  <TableCell align="right">
                    <Button
                      data-testid={`entitlement-settle-${entitlement.id}`}
                      variant="outlined"
                      size="small"
                      disabled
                      onClick={() => {
                        setSelectedId(entitlement.id);
                        setSettlementReference("");
                        setSettlementNote("");
                        setConfirmOpen(true);
                      }}
                    >
                      {t("Settle")}</Button>
                  </TableCell>
                </TableRow>
              ))}
              {entitlementsQuery.data && entitlements.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10}>
                    <Alert severity="info">{t("No prize entitlements match the current filters.")}</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        <AdminTablePagination
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
        <DialogTitle>{t("Settle entitlement")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {settleMutation.isError ? (
              <Alert
                severity="error"
                action={
                  <Button color="inherit" size="small" onClick={() => settleMutation.reset()}>
                    {t("Dismiss")}</Button>
                }
              >
                {t(getUserFacingErrorMessage(settleMutation.error) ?? "Something went wrong. Please try again.")}
              </Alert>
            ) : null}
            <TextField
              label={t("Settlement reference")}
              value={settlementReference}
              onChange={(event) => setSettlementReference(event.target.value)}
              fullWidth
              required
            />
            <TextField
              label={t("Settlement note")}
              value={settlementNote}
              onChange={(event) => setSettlementNote(event.target.value)}
              fullWidth
              multiline
              minRows={3}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => settleMutation.mutate()}
            disabled={settleMutation.isPending || settlementReference.trim().length === 0}
          >
            {t("Confirm settlement")}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
    </AdminTablePage>
  );
}
