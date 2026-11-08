import { useMutation } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  auditActivityReport,
  depositReport,
  playerVerificationReport,
  prizeEntitlementsReport,
  sessionActivityReport,
  supportTicketReport,
  type AdminAuditLogSummaryResponse,
  type AdminSessionSummaryResponse,
  type DepositReportResponse,
  type PlayerVerificationReviewResponse,
  type PrizeEntitlementResponse,
  type SupportTicketSummaryResponse,
} from "../../api/admin";
import { useSession } from "../../api/session";
import { getUserFacingErrorMessage, type PageResponse } from "../../api/types";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";

type ReportKind =
  | "supportTickets"
  | "auditActivity"
  | "sessionActivity"
  | "playerVerifications"
  | "depositActivity"
  | "prizeEntitlements";
type ReportResult = {
  kind: ReportKind;
  from: string;
  to: string;
  query: string;
  status: string;
  headers: string[];
  rows: string[][];
};

const reportTitles: Record<ReportKind, string> = {
  supportTickets: "Support tickets",
  auditActivity: "Admin audit activity",
  sessionActivity: "Game session activity",
  playerVerifications: "Player verification activity",
  depositActivity: "Deposit review activity",
  prizeEntitlements: "Prize entitlement activity",
};

const reportDateBasis: Record<ReportKind, string> = {
  supportTickets: "Support tickets are filtered by creation date.",
  auditActivity: "Audit entries are filtered by event date.",
  sessionActivity: "Sessions are filtered by scheduled start date.",
  playerVerifications: "Verification reviews are filtered by review date.",
  depositActivity: "Deposits are filtered by submission date.",
  prizeEntitlements: "Prize entitlements are filtered by creation date.",
};

const reportStatuses: Record<ReportKind, string[]> = {
  supportTickets: ["OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"],
  auditActivity: [],
  sessionActivity: ["DRAFT", "OPEN", "STARTED", "DRAWING", "CLOSED", "SETTLED", "CANCELLED"],
  playerVerifications: ["IN_REVIEW", "VERIFIED", "NEEDS_INFORMATION", "REJECTED"],
  depositActivity: ["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION", "APPROVED", "REJECTED"],
  prizeEntitlements: ["PENDING", "SETTLED", "VOID"],
};

const maxReportRows = 100_000;

function dateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function csvCell(value: string) {
  const safeValue = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return `"${safeValue.replace(/"/g, '""')}"`;
}

function downloadCsv(result: ReportResult) {
  const csv = [result.headers, ...result.rows].map((row) => row.map(csvCell).join(",")).join("\r\n");
  const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${result.kind}-${result.from}-to-${result.to}.csv`;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

async function collectReportPages<T>(loadPage: (page: number, size: number) => Promise<PageResponse<T>>) {
  const records: T[] = [];
  for (let page = 0; page < maxReportRows / 100; page += 1) {
    const result = await loadPage(page, 100);
    if (result.totalElements > maxReportRows) {
      throw new Error(`This report contains more than ${maxReportRows.toLocaleString()} records. Add a search or status filter, or choose a shorter date range.`);
    }
    records.push(...result.content);
    if (result.last) return records;
  }
  throw new Error(`This report exceeds the ${maxReportRows.toLocaleString()} record export limit. Add a search or status filter, or choose a shorter date range.`);
}

export function ReportsPage() {
  const { t } = useTranslation();
  const session = useSession();
  const isAdmin = Boolean(session?.user.roles.includes("ADMIN"));
  const today = dateInputValue(new Date());
  const defaultFrom = dateInputValue(new Date(Date.now() - 6 * 24 * 60 * 60 * 1000));
  const [kind, setKind] = useState<ReportKind>("supportTickets");
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(today);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const activeStatuses = reportStatuses[kind];

  const reportMutation = useMutation({
    mutationFn: async (): Promise<ReportResult> => {
      const fromDate = new Date(`${from}T00:00:00`);
      const throughDate = new Date(`${to}T00:00:00`);
      if (!Number.isFinite(fromDate.getTime()) || !Number.isFinite(throughDate.getTime()) || fromDate > throughDate) {
        throw new Error("Choose a valid date range.");
      }
      const endExclusive = new Date(throughDate);
      endExclusive.setDate(endExclusive.getDate() + 1);
      if (endExclusive.getTime() - fromDate.getTime() > 366 * 24 * 60 * 60 * 1000) {
        throw new Error("Choose a date range of 366 days or less.");
      }
      const fromInstant = fromDate.toISOString();
      const throughInstant = endExclusive.toISOString();
      const searchQuery = query.trim();
      const reportContext = { kind, from, to, query: searchQuery, status };
      if (kind === "supportTickets") {
        const rows = await collectReportPages<SupportTicketSummaryResponse>(
          (page, size) => supportTicketReport(
            fromInstant, throughInstant, page, size, searchQuery, status || undefined,
          ),
        );
        return {
          ...reportContext,
          headers: ["Ticket ID", "Subject", "Category", "Priority", "Status", "Assigned user ID", "Created", "Updated"],
          rows: rows.map((row) => [String(row.ticketNumber || row.id), row.subject, row.category, row.priority,
            row.status, row.assignedToUserId == null ? "" : String(row.assignedToUserId), row.createdAt, row.updatedAt]),
        };
      }
      if (kind === "auditActivity") {
        const rows = await collectReportPages<AdminAuditLogSummaryResponse>(
          (page, size) => auditActivityReport(fromInstant, throughInstant, page, size, searchQuery),
        );
        return {
          ...reportContext,
          headers: ["Log ID", "Actor user ID", "Action", "Target type", "Target ID", "Reason", "Created"],
          rows: rows.map((row) => [String(row.id), row.actorUserId == null ? "" : String(row.actorUserId), row.action,
            row.targetType, row.targetId == null ? "" : String(row.targetId), row.reason ?? "", row.createdAt]),
        };
      }
      if (kind === "sessionActivity") {
        const rows = await collectReportPages<AdminSessionSummaryResponse>(
          (page, size) => sessionActivityReport(
            fromInstant, throughInstant, page, size, searchQuery, status || undefined,
          ),
        );
        return {
          ...reportContext,
          headers: ["Session ID", "Room", "Session", "State", "Scheduled start", "Started", "Ended",
            "Ticket price", "Currency", "Tickets sold", "Draws", "Win claims", "Support tickets"],
          rows: rows.map((row) => [String(row.id), row.roomName, row.sessionName, row.state, row.scheduledStartAt,
            row.startedAt ?? "", row.endedAt ?? "", String(row.ticketPrice), row.currency, String(row.ticketCount),
            String(row.drawCount), String(row.winClaimCount), String(row.supportTicketCount)]),
        };
      }
      if (kind === "playerVerifications") {
        const rows = await collectReportPages<PlayerVerificationReviewResponse>(
          (page, size) => playerVerificationReport(
            fromInstant, throughInstant, page, size, searchQuery, status || undefined,
          ),
        );
        return {
          ...reportContext,
          headers: ["Review ID", "Player ID", "Reviewer user ID", "Status", "Reason", "Reviewed"],
          rows: rows.map((row) => [String(row.id), String(row.playerId), String(row.reviewerUserId), row.status,
            row.reason, row.reviewedAt]),
        };
      }
      if (kind === "depositActivity") {
        const rows = await collectReportPages<DepositReportResponse>(
          (page, size) => depositReport(
            fromInstant, throughInstant, page, size, searchQuery, status || undefined,
          ),
        );
        return {
          ...reportContext,
          headers: ["Deposit ID", "Player ID", "Submitted amount", "Credited amount", "Currency", "Status",
            "Submitted", "Verified", "Reviewed"],
          rows: rows.map((row) => [String(row.id), String(row.playerId), String(row.submittedCashAmount),
            String(row.calculatedCreditAmount), row.currency, row.status, row.submittedAt, row.verifiedAt ?? "",
            row.reviewedAt ?? ""]),
        };
      }
      const rows = await collectReportPages<PrizeEntitlementResponse>(
        (page, size) => prizeEntitlementsReport(
          fromInstant, throughInstant, page, size, searchQuery, status || undefined,
        ),
      );
      return {
        ...reportContext,
        headers: ["Entitlement ID", "Win claim ID", "Session ID", "Ticket ID", "Player ID", "Prize type",
          "Settlement path", "Status", "Prize amount", "Currency", "Operation ID", "Created", "Settled",
          "Settled by", "Settlement reference", "Settlement note"],
        rows: rows.map((row) => [String(row.id), String(row.winClaimId), String(row.sessionId), String(row.ticketId),
          String(row.claimantId), row.prizeType, row.settlementPath, row.status, row.prizeAmount, row.currency,
          row.operationId, row.createdAt, row.settledAt ?? "", row.settledBy == null ? "" : String(row.settledBy),
          row.settlementReference ?? "", row.settlementNote ?? ""]),
      };
    },
  });

  const result = reportMutation.data;
  const previewRows = result?.rows.slice(0, 15) ?? [];

  return (
    <AdminTablePage>
    <Stack spacing={3}>
      <AdminPageHeader title={t("Reports")} description={t("Generate date-filtered operational reports and export them as CSV.")} />
      <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", borderRadius: "12px" }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" fontWeight={700}>{t("Report filters")}</Typography>
            <Typography variant="body2" color="text.secondary">{t(reportDateBasis[kind])}</Typography>
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: {
                  xs: "minmax(0, 1fr)",
                  sm: "repeat(2, minmax(0, 1fr))",
                  lg: "repeat(auto-fit, minmax(min(100%, 190px), 1fr))",
                },
                alignItems: "center",
                gap: 2,
                minWidth: 0,
              }}
            >
              <FormControl fullWidth>
                <InputLabel id="report-type-label">{t("Report type")}</InputLabel>
                <Select id="report-type" labelId="report-type-label" label={t("Report type")} value={kind}
                  onChange={(event) => {
                    setKind(event.target.value as ReportKind);
                    setStatus("");
                  }}>
                  <MenuItem value="supportTickets">{t("Support tickets")}</MenuItem>
                  {isAdmin ? <MenuItem value="auditActivity">{t("Admin audit activity")}</MenuItem> : null}
                  {isAdmin ? <MenuItem value="sessionActivity">{t("Game session activity")}</MenuItem> : null}
                  {isAdmin ? <MenuItem value="playerVerifications">{t("Player verification activity")}</MenuItem> : null}
                  {isAdmin ? <MenuItem value="prizeEntitlements">{t("Prize entitlement activity")}</MenuItem> : null}
                  {session?.user.roles.some((role) => ["ADMIN", "FINANCE_VERIFIER", "FINANCE_APPROVER"].includes(role))
                    ? <MenuItem value="depositActivity">{t("Deposit review activity")}</MenuItem>
                    : null}
                </Select>
              </FormControl>
              <TextField fullWidth type="date" label={t("From")} value={from} onChange={(event) => setFrom(event.target.value)} InputLabelProps={{ shrink: true }} />
              <TextField fullWidth type="date" label={t("To")} value={to} onChange={(event) => setTo(event.target.value)} InputLabelProps={{ shrink: true }} />
              <TextField
                fullWidth
                label={t("Search")}
                placeholder={t("Name, ID, reference, or action")}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                inputProps={{ maxLength: 160 }}
              />
              {activeStatuses.length ? (
                <FormControl fullWidth>
                  <InputLabel id="report-status-label">{t("Status")}</InputLabel>
                  <Select
                    id="report-status"
                    labelId="report-status-label"
                    label={t("Status")}
                    value={status}
                    onChange={(event) => setStatus(event.target.value)}
                  >
                    <MenuItem value="">{t("All statuses")}</MenuItem>
                    {activeStatuses.map((value) => <MenuItem key={value} value={value}>{t(value)}</MenuItem>)}
                  </Select>
                </FormControl>
              ) : null}
              <Button
                variant="contained"
                onClick={() => reportMutation.mutate()}
                disabled={reportMutation.isPending}
                sx={{ justifySelf: "stretch", whiteSpace: "nowrap" }}
              >
                {reportMutation.isPending ? t("Generating…") : t("Generate report")}
              </Button>
            </Box>
            {reportMutation.isError ? <Alert severity="error">{t(getUserFacingErrorMessage(reportMutation.error) ?? reportMutation.error.message)}</Alert> : null}
          </Stack>
        </CardContent>
      </Card>

      {result ? (
        <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", borderRadius: "12px", minWidth: 0 }}>
          <CardContent>
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems={{ sm: "center" }} gap={1}>
                <Box>
                  <Typography variant="h6" fontWeight={700}>{t(reportTitles[result.kind])}</Typography>
                  <Typography color="text.secondary">{t("{{count}} records from {{from}} to {{to}}", { count: result.rows.length, from: result.from, to: result.to })}</Typography>
                  {result.query || result.status ? (
                    <Typography variant="caption" color="text.secondary">
                      {[result.query ? `${t("Search")}: ${result.query}` : "", result.status ? `${t("Status")}: ${t(result.status)}` : ""]
                        .filter(Boolean)
                        .join(" · ")}
                    </Typography>
                  ) : null}
                </Box>
                <Button variant="outlined" onClick={() => downloadCsv(result)} disabled={!result.rows.length}>{t("Download CSV")}</Button>
              </Stack>
              {result.rows.length ? (
                <Box sx={{ overflowX: "auto", maxWidth: "100%" }}>
                  <Table size="small" aria-label={t("Report preview")}>
                    <TableHead><TableRow>{result.headers.map((header) => <TableCell key={header}>{t(header)}</TableCell>)}</TableRow></TableHead>
                    <TableBody>{previewRows.map((row, rowIndex) => (
                      <TableRow key={`${row[0]}-${rowIndex}`}>{row.map((cell, index) => <TableCell key={`${index}-${cell}`}>{cell || "—"}</TableCell>)}</TableRow>
                    ))}</TableBody>
                  </Table>
                  {result.rows.length > previewRows.length ? <Typography variant="caption" color="text.secondary">{t("Showing the first 15 rows. Download CSV for the full report.")}</Typography> : null}
                </Box>
              ) : <Alert severity="info">{t("No records found for this date range.")}</Alert>}
            </Stack>
          </CardContent>
        </Card>
      ) : null}
    </Stack>
    </AdminTablePage>
  );
}
