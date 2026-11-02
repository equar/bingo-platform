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
  InputAdornment,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useState } from "react";
import { auditLogDetail, auditLogs, type AdminAuditLogSummaryResponse } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";

function hasDetailedMetadata(metadataJson: string) {
  try {
    const metadata = JSON.parse(metadataJson) as Record<string, unknown>;
    return metadata.schemaVersion === 1 && "outcome" in metadata && "arguments" in metadata;
  } catch {
    return false;
  }
}

export function AuditLogsPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [actionFilter, setActionFilter] = useState("ALL");
  const [targetTypeFilter, setTargetTypeFilter] = useState("ALL");
  const [dateFilter, setDateFilter] = useState("ALL");
  const [selectedEntry, setSelectedEntry] = useState<AdminAuditLogSummaryResponse | null>(null);
  const auditQuery = useQuery({
    queryKey: ["audit-logs", page, pageSize],
    queryFn: () => auditLogs(page, pageSize),
  });
  const detailQuery = useQuery({
    queryKey: ["audit-logs", "detail", selectedEntry?.id],
    queryFn: () => auditLogDetail(selectedEntry!.id),
    enabled: selectedEntry !== null,
  });

  if (auditQuery.isLoading) {
    return <StatusState title={t("Loading audit logs")} loading message={t("Fetching recent audit history")} />;
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
  const query = search.trim().toLocaleLowerCase();
  const filteredEntries = entries.filter((entry) => {
    const searchableText = [
      entry.id,
      entry.actorUserId ?? "system",
      entry.action,
      entry.targetType,
      entry.targetId ?? "",
      entry.reason ?? "",
      entry.operationId ?? "",
    ]
      .join(" ")
      .toLocaleLowerCase();
    const matchesSearch = !query || searchableText.includes(query);
    const matchesAction = actionFilter === "ALL" || entry.action === actionFilter;
    const matchesTarget = targetTypeFilter === "ALL" || entry.targetType === targetTypeFilter;
    const age = Date.now() - new Date(entry.createdAt).getTime();
    const matchesDate =
      dateFilter === "ALL" ||
      (dateFilter === "24H" && age <= 24 * 60 * 60 * 1000) ||
      (dateFilter === "7D" && age <= 7 * 24 * 60 * 60 * 1000) ||
      (dateFilter === "30D" && age <= 30 * 24 * 60 * 60 * 1000);
    return matchesSearch && matchesAction && matchesTarget && matchesDate;
  });
  const actions = [...new Set(entries.map((entry) => entry.action))].sort();
  const targetTypes = [...new Set(entries.map((entry) => entry.targetType))].sort();

  return (
    <Stack spacing={2.5}>
      <Box>
        <Typography variant="h4">{t("Audit logs")}</Typography>
        <Typography color="text.secondary">{t("Recent system activity and review actions.")}</Typography>
      </Box>

      {entries.length === 0 ? <Alert severity="info">{t("No audit entries found.")}</Alert> : null}

      {entries.length > 0 ? (
        <Stack spacing={1.25}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
            <TextField
              label={t("Search audit logs")}
              placeholder={t("Action, actor, target, reason, operation ID")}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 2, minWidth: { md: 280 } }}
              InputProps={{ startAdornment: <InputAdornment position="start">{t("⌕")}</InputAdornment> }}
            />
            <TextField
              select
              label={t("Action")}
              value={actionFilter}
              onChange={(event) => {
                setActionFilter(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 1, minWidth: 160 }}
            >
              <MenuItem value="ALL">{t("All actions")}</MenuItem>
              {actions.map((action) => (
                <MenuItem key={action} value={action}>
                  {action}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label={t("Target type")}
              value={targetTypeFilter}
              onChange={(event) => {
                setTargetTypeFilter(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 1, minWidth: 160 }}
            >
              <MenuItem value="ALL">{t("All target types")}</MenuItem>
              {targetTypes.map((targetType) => (
                <MenuItem key={targetType} value={targetType}>
                  {targetType}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label={t("Time period")}
              value={dateFilter}
              onChange={(event) => {
                setDateFilter(event.target.value);
                setPage(0);
              }}
              size="small"
              sx={{ flex: 1, minWidth: 150 }}
            >
              <MenuItem value="ALL">{t("Any time")}</MenuItem>
              <MenuItem value="24H">{t("Last 24 hours")}</MenuItem>
              <MenuItem value="7D">{t("Last 7 days")}</MenuItem>
              <MenuItem value="30D">{t("Last 30 days")}</MenuItem>
            </TextField>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {t("Showing")}{" "}{filteredEntries.length} {t("matching entries on this page")}</Typography>
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
                {filteredEntries.map((entry) => (
                  <TableRow key={entry.id} hover>
                    <TableCell>{entry.id}</TableCell>
                    <TableCell sx={{ whiteSpace: "nowrap" }}>{new Date(entry.createdAt).toLocaleString()}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{entry.action}</TableCell>
                    <TableCell>{entry.actorUserId ?? t("System")}</TableCell>
                    <TableCell sx={{ whiteSpace: "nowrap" }}>
                      {entry.targetType} {t("·")}{" "}{entry.targetId ?? "n/a"}
                    </TableCell>
                    <TableCell sx={{ maxWidth: 260, minWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {entry.reason ?? "—"}
                    </TableCell>
                    <TableCell sx={{ maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {entry.operationId ?? "—"}
                    </TableCell>
                    <TableCell align="right">
                      <Button size="small" onClick={() => setSelectedEntry(entry)}>
                        {t("View details")}</Button>
                    </TableCell>
                  </TableRow>
                ))}
                {filteredEntries.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                      {t("No audit entries match your search and filters.")}</TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={auditQuery.data?.totalElements ?? 0}
            page={page}
            onPageChange={(_, nextPage) => setPage(nextPage)}
            rowsPerPage={pageSize}
            onRowsPerPageChange={(event) => {
              setPageSize(Number(event.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50, 100]}
          />
        </Stack>
      ) : null}

      <Dialog open={Boolean(selectedEntry)} onClose={() => setSelectedEntry(null)} fullWidth maxWidth="sm">
        <DialogTitle>{t("Audit log details")}</DialogTitle>
        <DialogContent>
          {detailQuery.isLoading ? <CircularProgress size={24} aria-label={t("Loading audit log details")} /> : null}
          {detailQuery.error ? (
            <Alert
              severity="error"
              action={
                <Button color="inherit" size="small" onClick={() => detailQuery.refetch()}>
                  {t("Retry")}</Button>
              }
            >
              {t("Unable to load the full audit details.")}</Alert>
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
                  <Typography variant="caption" color="text.secondary">
                    {t(String(label))}
                  </Typography>
                  <Typography sx={{ overflowWrap: "anywhere", whiteSpace: "pre-wrap" }}>{value}</Typography>
                </Box>
              ))}
              {detailQuery.data.metadataJson ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    {t("Recorded context")}</Typography>
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
                      {t("This is a legacy entry with limited context. New audit entries include redacted request and outcome details.")}</Alert>
                  ) : null}
                </Box>
              ) : (
                <Alert severity="info">
                  {t("This older audit entry has no recorded context. New entries include redacted request and outcome details.")}</Alert>
              )}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedEntry(null)}>{t("Close")}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
