import { useTranslation } from "react-i18next";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Alert, Box, Button, Chip, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { deposits } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { useSession } from "../../api/session";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { translateAdminStatus } from "../../i18n";

const statusOptions = ["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION", "APPROVED", "REJECTED"] as const;
const maskPaymentReference = (reference: string) => `${"*".repeat(Math.max(0, reference.length - 4))}${reference.slice(-4)}`;

export function DepositsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const session = useSession();
  const isAdmin = session?.user.roles.includes("ADMIN") ?? false;
  const canVerify = !isAdmin && (session?.user.roles.includes("SUPPORT") ?? false);
  const [searchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("deposits");
  const [status, setStatus] = useListSessionState("deposits:status", () => statusOptions.find((option) => option === searchParams.get("status")) ?? "");
  const [page, setPage] = useListSessionState("deposits:page", 0);
  const [pageSize, setPageSize] = useListSessionState("deposits:size", 20);
  const normalizedQuery = query.trim();
  const result = useQuery({ queryKey: ["deposits", normalizedQuery, status, page, pageSize], queryFn: () => deposits(page, pageSize, normalizedQuery || undefined, status || undefined), placeholderData: keepPreviousData });
  useEffect(() => { if (searchParams.has("status")) setStatus(statusOptions.find((option) => option === searchParams.get("status")) ?? ""); }, [searchParams, setStatus]);

  if (result.isLoading) return <StatusState title={t("Loading deposits")} loading message={t("Fetching deposit queue")} />;
  if (result.error && !result.data) return <StatusState title={t("Unable to load deposits")} message={t(getUserFacingErrorMessage(result.error) ?? "Something went wrong. Please try again.")} actionLabel={t("Retry")} onAction={() => result.refetch()} />;
  return <AdminTablePage><Stack spacing={3}>
    <AdminPageHeader title={t("Deposits")} description={t("Review player deposit requests.")} refresh={{ isFetching: result.isFetching, failed: result.isRefetchError, error: result.error, onRefresh: () => result.refetch() }} />
    <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
      <TextField label={t("Search deposit, player, payment reference, or operation")} value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} fullWidth />
      <TextField select label={t("Status")} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} sx={{ minWidth: { xs: "100%", md: 220 } }}>
        <MenuItem value="">{t("All statuses")}</MenuItem>{statusOptions.map((option) => <MenuItem key={option} value={option}>{translateAdminStatus(option, t)}</MenuItem>)}
      </TextField>
    </Stack>
    {result.isFetching && !result.isLoading ? <Alert severity="info">{t("Updating deposit results...")}</Alert> : null}
    <Box sx={{ overflowX: "auto" }}><Table size="small" aria-label={t("deposit reviews table")}><TableHead><TableRow>
      {["Deposit", "Player", "Phone number", "Payment reference", "Submitted cash", "Credit", "Status", "Submitted", "Review note", "Actions"].map((label, index) => <TableCell key={label} align={index === 4 || index === 5 || index === 9 ? "right" : undefined}>{t(label)}</TableCell>)}
    </TableRow></TableHead><TableBody>
      {(result.data?.content ?? []).map((d) => <TableRow key={d.id} hover data-testid={`deposit-row-${d.id}`}>
        <TableCell><Stack spacing={0.25}><Button data-testid={`deposit-details-${d.id}`} aria-label={`${t("View details for deposit")} #${d.id}`} onClick={() => navigate(`/deposits/${d.id}`)} sx={{ alignSelf: "flex-start", minWidth: 0, p: 0, fontWeight: 600, textTransform: "none" }}>{t("Deposit #")}{d.id}</Button><Typography variant="caption" color="text.secondary">{d.operationId}</Typography></Stack></TableCell>
        <TableCell><Stack spacing={0.25}><Typography fontWeight={600}>{d.playerDisplayName || `${t("Player")} #${d.playerId}`}</Typography><Typography variant="caption" color="text.secondary">{t("Player #")}{d.playerId}</Typography></Stack></TableCell>
        <TableCell>{d.playerPhoneNumber ?? t("n/a")}</TableCell>
        <TableCell>{canVerify ? maskPaymentReference(d.normalizedTelebirrReference) : d.normalizedTelebirrReference}</TableCell>
        <TableCell align="right">{d.submittedCashAmount} {d.currency}</TableCell><TableCell align="right">{d.calculatedCreditAmount}</TableCell>
        <TableCell><Chip label={translateAdminStatus(d.status, t)} color={["PENDING", "VERIFICATION_EXCEPTION"].includes(d.status) ? "warning" : d.status === "VERIFIED" ? "success" : "default"} size="small" />{["PENDING", "AWAITING_VERIFICATION", "VERIFICATION_EXCEPTION"].includes(d.status) ? <Typography variant="caption" display="block" color="warning.main">{t("Awaiting payment reference verification")}</Typography> : null}{d.verifiedBy != null ? <Typography variant="caption" display="block" color="text.secondary">{t("Verified by user #")}{d.verifiedBy}</Typography> : null}</TableCell>
        <TableCell>{new Date(d.submittedAt).toLocaleString()}</TableCell><TableCell>{d.reviewNote ?? t("n/a")}</TableCell>
        <TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end">
          {isAdmin && d.status === "REJECTED" ? <Button data-testid={`deposit-review-${d.id}`} size="small" variant="outlined" onClick={() => navigate(`/deposits/${d.id}`)}>{t("Review")}</Button> : null}
          {canVerify && ["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION"].includes(d.status) ? <Button data-testid={`deposit-verify-${d.id}`} size="small" variant="contained" onClick={() => navigate(`/deposits/${d.id}`)}>{t("Verify and deposit")}</Button> : null}
        </Stack></TableCell>
      </TableRow>)}
      {result.data && result.data.content.length === 0 ? <TableRow><TableCell colSpan={10}><Alert severity="info">{t("No deposits match the current search and filters.")}</Alert></TableCell></TableRow> : null}
    </TableBody></Table></Box>
    <AdminTablePagination component="div" dataLoaded={!!result.data} count={result.data?.totalElements ?? 0} page={page} onPageChange={(_, next) => setPage(next)} rowsPerPage={pageSize} onRowsPerPageChange={(event) => { setPageSize(Number(event.target.value)); setPage(0); }} rowsPerPageOptions={[10, 20, 50]} />
  </Stack></AdminTablePage>;
}
