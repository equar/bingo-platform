import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
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
import { useState } from "react";
import {
  auditLogDetail,
  searchAuditLogs,
  type AdminAuditLogSummaryResponse,
  type AuditLogSearchFilters,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { useListSessionState } from "../../components/useListSessionState";

type AuditFilterDraft = {
  query: string;
  actorUserId: string;
  auditLogId: string;
  action: string;
  targetType: string;
  targetId: string;
  operationId: string;
  from: string;
  to: string;
};

const emptyDraft: AuditFilterDraft = {
  query: "",
  actorUserId: "",
  auditLogId: "",
  action: "",
  targetType: "",
  targetId: "",
  operationId: "",
  from: "",
  to: "",
};

function hasDetailedMetadata(metadataJson: string) {
  try {
    const metadata = JSON.parse(metadataJson) as Record<string, unknown>;
    return metadata.schemaVersion === 1 && "outcome" in metadata && "arguments" in metadata;
  } catch {
    return false;
  }
}

function parseOptionalId(value: string) {
  if (!value.trim()) return undefined;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1) {
    throw new Error("IDs must be positive whole numbers.");
  }
  return parsed;
}

function toSearchFilters(draft: AuditFilterDraft): AuditLogSearchFilters {
  if (draft.from && draft.to && draft.from > draft.to) {
    throw new Error("The start date must be on or before the end date.");
  }

  return {
    query: draft.query.trim() || undefined,
    actorUserId: parseOptionalId(draft.actorUserId),
    auditLogId: parseOptionalId(draft.auditLogId),
    action: draft.action.trim() || undefined,
    targetType: draft.targetType.trim() || undefined,
    targetId: parseOptionalId(draft.targetId),
    operationId: draft.operationId.trim() || undefined,
    from: draft.from ? `${draft.from}T00:00:00.000Z` : undefined,
    to: draft.to ? `${draft.to}T23:59:59.999Z` : undefined,
  };
}

function csvCell(value: unknown) {
  let text = value == null ? "" : String(value);
  if (/^[=+\-@]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

function downloadAuditCsv(entries: AdminAuditLogSummaryResponse[]) {
  const columns = [
    "ID",
    "Time",
    "Actor ID",
    "Actor",
    "Action",
    "Target type",
    "Target ID",
    "Reason",
    "Operation ID",
  ];
  const rows = entries.map((entry) => [
    entry.id,
    entry.createdAt,
    entry.actorUserId,
    entry.actorDisplayName,
    entry.action,
    entry.targetType,
    entry.targetId,
    entry.reason,
    entry.operationId,
  ]);
  const csv = [columns, ...rows].map((row) => row.map(csvCell).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `audit-logs-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

export function AuditLogsPage() {
  const { t } = useTranslation();
  const [page, setPage] = useListSessionState("audit-logs:page", 0);
  const [pageSize, setPageSize] = useListSessionState("audit-logs:size", 20);
  const [draftFilters, setDraftFilters] = useListSessionState("audit-logs:draft", emptyDraft);
  const [appliedFilters, setAppliedFilters] = useListSessionState<AuditLogSearchFilters>("audit-logs:filters", {});
  const [filterError, setFilterError] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [selectedEntry, setSelectedEntry] = useState<AdminAuditLogSummaryResponse | null>(null);
  const auditQuery = useQuery({
    queryKey: ["audit-logs", page, pageSize, appliedFilters],
    queryFn: () => searchAuditLogs(page, pageSize, appliedFilters),
  });
  const detailQuery = useQuery({
    queryKey: ["audit-logs", "detail", selectedEntry?.id],
    queryFn: () => auditLogDetail(selectedEntry!.id),
    enabled: selectedEntry !== null,
  });

  if (auditQuery.isLoading) {
    return <StatusState title={t("Loading audit logs")} loading message={t("Fetching audit history")} />;
  }

  if (auditQuery.error) {
    return (
      <StatusState
        title={t("Unable to load audit logs")}
        message={t(getUserFacingErrorMessage(auditQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => auditQuery.refetch()}
      />
    );
  }

  const entries = auditQuery.data?.content ?? [];
  const totalEntries = auditQuery.data?.totalElements ?? 0;
  const hasFilters = Object.values(appliedFilters).some((value) => value !== undefined && value !== "");

  const applyFilters = () => {
    try {
      setAppliedFilters(toSearchFilters(draftFilters));
      setPage(0);
      setFilterError(null);
    } catch (error) {
      setFilterError(error instanceof Error ? error.message : t("Invalid audit search filters."));
    }
  };

  const clearFilters = () => {
    setDraftFilters(emptyDraft);
    setAppliedFilters({});
    setFilterError(null);
    setExportError(null);
    setPage(0);
  };

  const exportFilteredEntries = async () => {
    setIsExporting(true);
    setExportError(null);
    try {
      const allEntries: AdminAuditLogSummaryResponse[] = [];
      let exportPage = 0;
      let totalPages = 1;
      while (exportPage < totalPages) {
        const result = await searchAuditLogs(exportPage, 100, appliedFilters);
        allEntries.push(...result.content);
        totalPages = result.totalPages;
        exportPage += 1;
      }
      downloadAuditCsv(allEntries);
    } catch (error) {
      setExportError(
        t(getUserFacingErrorMessage(error) ?? "Unable to export the matching audit entries."),
      );
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <AdminTablePage>
    <Stack spacing={2.5}>
      <AdminPageHeader title={t("Audit logs")} description={t("Search and trace activity across the full audit history. Newest activity is shown first.")} />

      <Stack
        component="form"
        spacing={1.5}
        onSubmit={(event) => {
          event.preventDefault();
          applyFilters();
        }}
      >
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" },
            gap: 1.5,
          }}
        >
          <TextField
            label={t("Search all audit fields")}
            placeholder={t("Actor, action, target, reason, operation ID, request details")}
            value={draftFilters.query}
            onChange={(event) => setDraftFilters({ ...draftFilters, query: event.target.value })}
            size="small"
          />
          <TextField
            label={t("Actor user ID")}
            type="number"
            inputProps={{ min: 1, step: 1 }}
            value={draftFilters.actorUserId}
            onChange={(event) => setDraftFilters({ ...draftFilters, actorUserId: event.target.value })}
            size="small"
          />
          <TextField
            label={t("Audit log ID")}
            type="number"
            inputProps={{ min: 1, step: 1 }}
            value={draftFilters.auditLogId}
            onChange={(event) => setDraftFilters({ ...draftFilters, auditLogId: event.target.value })}
            size="small"
          />
          <TextField
            label={t("Action")}
            placeholder={t("Exact action, e.g. UPDATE_SESSION_STATE")}
            value={draftFilters.action}
            onChange={(event) => setDraftFilters({ ...draftFilters, action: event.target.value })}
            size="small"
          />
          <TextField
            label={t("Target type")}
            placeholder={t("Exact target type")}
            value={draftFilters.targetType}
            onChange={(event) => setDraftFilters({ ...draftFilters, targetType: event.target.value })}
            size="small"
          />
          <TextField
            label={t("Target ID")}
            type="number"
            inputProps={{ min: 1, step: 1 }}
            value={draftFilters.targetId}
            onChange={(event) => setDraftFilters({ ...draftFilters, targetId: event.target.value })}
            size="small"
          />
          <TextField
            label={t("Operation ID")}
            value={draftFilters.operationId}
            onChange={(event) => setDraftFilters({ ...draftFilters, operationId: event.target.value })}
            size="small"
          />
          <TextField
            label={t("From date")}
            type="date"
            value={draftFilters.from}
            onChange={(event) => setDraftFilters({ ...draftFilters, from: event.target.value })}
            size="small"
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label={t("To date")}
            type="date"
            value={draftFilters.to}
            onChange={(event) => setDraftFilters({ ...draftFilters, to: event.target.value })}
            size="small"
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Box>
        <Typography variant="caption" color="text.secondary">
          {t("Date filters use UTC calendar days. Leave them blank to search all available history.")}
        </Typography>
        {filterError ? <Alert severity="error">{t(filterError)}</Alert> : null}
        {exportError ? <Alert severity="error">{exportError}</Alert> : null}
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1} justifyContent="space-between">
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <Button type="submit" variant="contained">{t("Search audit logs")}</Button>
            <Button type="button" onClick={clearFilters}>{t("Clear filters")}</Button>
          </Stack>
          <Button
            type="button"
            variant="outlined"
            onClick={() => void exportFilteredEntries()}
            disabled={isExporting || totalEntries === 0}
          >
            {isExporting ? t("Exporting...") : t("Export matching CSV")}
          </Button>
        </Stack>
      </Stack>

      {totalEntries === 0 ? (
        <Alert severity="info">
          {hasFilters ? t("No audit entries match your search and filters.") : t("No audit entries found.")}
        </Alert>
      ) : (
        <Typography variant="body2" color="text.secondary">
          {t("Showing")} {page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalEntries)} {t("of")}{" "}
          {totalEntries} {t("matching entries across all pages")}
        </Typography>
      )}

      <TableContainer component={Paper} variant="outlined">
        <Table size="small" sx={{ minWidth: 1050 }} aria-label={t("Audit logs")}>
          <TableHead>
            <TableRow>
              <TableCell>{t("ID")}</TableCell>
              <TableCell>{t("Time")}</TableCell>
              <TableCell>{t("Action")}</TableCell>
              <TableCell>{t("Actor")}</TableCell>
              <TableCell>{t("Target")}</TableCell>
              <TableCell>{t("Reason")}</TableCell>
              <TableCell>{t("Operation ID")}</TableCell>
              <TableCell align="right">{t("Details")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {entries.map((entry) => (
              <TableRow key={entry.id} hover>
                <TableCell>{entry.id}</TableCell>
                <TableCell sx={{ whiteSpace: "nowrap" }}>{new Date(entry.createdAt).toLocaleString()}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{entry.action}</TableCell>
                <TableCell>
                  {entry.actorDisplayName
                    ? `${entry.actorDisplayName} (${entry.actorUserId})`
                    : entry.actorUserId ?? t("System")}
                </TableCell>
                <TableCell sx={{ whiteSpace: "nowrap" }}>
                  {entry.targetType} {t("·")} {entry.targetId ?? "n/a"}
                </TableCell>
                <TableCell
                  sx={{
                    maxWidth: 260,
                    minWidth: 180,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {entry.reason ?? "—"}
                </TableCell>
                <TableCell sx={{ maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {entry.operationId ?? "—"}
                </TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => setSelectedEntry(entry)}>{t("View details")}</Button>
                </TableCell>
              </TableRow>
            ))}
            {entries.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                  {t("No audit entries match your search and filters.")}
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </TableContainer>
      <AdminTablePagination
        component="div"
        count={totalEntries}
        page={page}
        onPageChange={(_, nextPage) => setPage(nextPage)}
        rowsPerPage={pageSize}
        onRowsPerPageChange={(event) => {
          setPageSize(Number(event.target.value));
          setPage(0);
        }}
        rowsPerPageOptions={[10, 20, 50, 100]}
      />

      <Dialog open={Boolean(selectedEntry)} onClose={() => setSelectedEntry(null)} fullWidth maxWidth="sm">
        <DialogTitle>{t("Audit log details")}</DialogTitle>
        <DialogContent>
          {detailQuery.isLoading ? <CircularProgress size={24} aria-label={t("Loading audit log details")} /> : null}
          {detailQuery.error ? (
            <Alert
              severity="error"
              action={
                <Button color="inherit" size="small" onClick={() => detailQuery.refetch()}>
                  {t("Retry")}
                </Button>
              }
            >
              {t("Unable to load the full audit details.")}
            </Alert>
          ) : null}
          {selectedEntry && detailQuery.data ? (
            <Stack spacing={1.5} divider={<Divider flexItem />} sx={{ pt: 1 }}>
              {[
                ["Log ID", detailQuery.data.id],
                ["Time", new Date(detailQuery.data.createdAt).toLocaleString()],
                ["Action", detailQuery.data.action],
                [
                  "Actor",
                  detailQuery.data.actorDisplayName
                    ? `${detailQuery.data.actorDisplayName} (${detailQuery.data.actorUserId})`
                    : detailQuery.data.actorUserId ?? t("System"),
                ],
                ["Target type", detailQuery.data.targetType],
                ["Target ID", detailQuery.data.targetId ?? "n/a"],
                ["Reason", detailQuery.data.reason ?? t("No reason provided")],
                ["Operation ID", detailQuery.data.operationId ?? "n/a"],
              ].map(([label, value]) => (
                <Box key={label}>
                  <Typography variant="caption" color="text.secondary">{t(String(label))}</Typography>
                  <Typography sx={{ overflowWrap: "anywhere", whiteSpace: "pre-wrap" }}>{value}</Typography>
                </Box>
              ))}
              {detailQuery.data.metadataJson ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>{t("Recorded context")}</Typography>
                  <Box
                    component="pre"
                    sx={{
                      m: 0,
                      p: 1.5,
                      maxHeight: 320,
                      overflow: "auto",
                      borderRadius: 1,
                      bgcolor: "action.hover",
                      fontFamily: "monospace",
                      fontSize: "0.8rem",
                      whiteSpace: "pre-wrap",
                      overflowWrap: "anywhere",
                    }}
                  >
                    {(() => {
                      try {
                        return JSON.stringify(JSON.parse(detailQuery.data.metadataJson), null, 2);
                      } catch {
                        return detailQuery.data.metadataJson;
                      }
                    })()}
                  </Box>
                  {!hasDetailedMetadata(detailQuery.data.metadataJson) ? (
                    <Alert severity="info" sx={{ mt: 1 }}>
                      {t("This is a legacy entry with limited context. New audit entries include redacted request and outcome details.")}
                    </Alert>
                  ) : null}
                </Box>
              ) : (
                <Alert severity="info">
                  {t("This older audit entry has no recorded context. New entries include redacted request and outcome details.")}
                </Alert>
              )}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedEntry(null)}>{t("Close")}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
    </AdminTablePage>
  );
}
