import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { useTranslation } from "react-i18next";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import {
  Alert,
  Box,
  Chip,
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
  Link as RouterLink,
  useLocation,
  useSearchParams,
} from "react-router-dom";
import { supportAssignees, supportTickets } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { translateAdminStatus, translateAdminValue } from "../../i18n";

const statusOptions = [
  "",
  "OPEN",
  "IN_PROGRESS",
  "WAITING_FOR_CUSTOMER",
  "RESOLVED",
  "CLOSED",
];
const categoryOptions = [
  "",
  "ACCOUNT",
  "WALLET",
  "GAME",
  "AGENT",
  "TECHNICAL",
  "BILLING",
  "OTHER",
];
const priorityOptions = ["LOW", "NORMAL", "HIGH", "URGENT"];

export function SupportTicketsPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("support-tickets");
  const status = searchParams.get("status") ?? "";
  const category = searchParams.get("category") ?? "";
  const priority = searchParams.get("priority") ?? "";
  const assignee = searchParams.get("assignee") ?? "";
  const pageValue = Number(searchParams.get("page") ?? 0);
  const page = Number.isSafeInteger(pageValue) ? Math.max(0, pageValue) : 0;
  const sizeValue = Number(searchParams.get("size") ?? 20);
  const pageSize = [10, 20, 50].includes(sizeValue) ? sizeValue : 20;
  const updateListState = (updates: Record<string, string>) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        Object.entries(updates).forEach(([key, value]) =>
          value ? next.set(key, value) : next.delete(key),
        );
        return next;
      },
      { replace: true },
    );
  useEffect(() => {
    if (!searchParams.has("query")) return;
    const safeParams = new URLSearchParams(searchParams);
    safeParams.delete("query");
    setSearchParams(safeParams, { replace: true });
  }, [searchParams, setSearchParams]);
  const normalizedQuery = query.trim();
  const usersQuery = useQuery({
    queryKey: ["support-assignees"],
    queryFn: supportAssignees,
  });
  const ticketsQuery = useQuery({
    queryKey: [
      "support-tickets",
      normalizedQuery,
      status,
      category,
      priority,
      assignee,
      page,
      pageSize,
    ],
    queryFn: () =>
      supportTickets(
        normalizedQuery || undefined,
        status || undefined,
        category || undefined,
        assignee || undefined,
        priority || undefined,
        page,
        pageSize,
      ),
    placeholderData: keepPreviousData,
  });

  const visibleTickets = ticketsQuery.data?.content ?? [];

  if (ticketsQuery.isLoading || usersQuery.isLoading) {
    return (
      <StatusState
        title={t("Loading support tickets")}
        loading
        message={t("Fetching call-center queue")}
      />
    );
  }

  if (ticketsQuery.error && !ticketsQuery.data) {
    return (
      <StatusState
        title={t("Unable to load support tickets")}
        message={t(
          getUserFacingErrorMessage(ticketsQuery.error) ??
            "Something went wrong. Please try again.",
        )}
        actionLabel={t("Retry")}
        onAction={() => ticketsQuery.refetch()}
      />
    );
  }

  return (
    <AdminTablePage>
      <Stack spacing={3}>
        <AdminPageHeader
          title={t("Support tickets")}
          description={t(
            "Queue, filter, and review call-center tickets with public and internal notes kept separate.",
          )}
          refresh={{
            isFetching: ticketsQuery.isFetching,
            failed: ticketsQuery.isRefetchError,
            error: ticketsQuery.error,
            onRefresh: () => ticketsQuery.refetch(),
          }}
        />

        <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
          <TextField
            label={t("Search tickets, subjects, players, or phone")}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              updateListState({ page: "" });
            }}
            fullWidth
          />
          <TextField
            select
            label={t("Status")}
            value={status}
            onChange={(event) => {
              updateListState({ status: event.target.value, page: "" });
            }}
            sx={{ minWidth: { xs: "100%", md: 200 } }}
          >
            <MenuItem value="">{t("All statuses")}</MenuItem>
            {statusOptions.filter(Boolean).map((option) => (
              <MenuItem key={option} value={option}>
                {translateAdminStatus(option, t)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label={t("Category")}
            value={category}
            onChange={(event) => {
              updateListState({ category: event.target.value, page: "" });
            }}
            sx={{ minWidth: { xs: "100%", md: 180 } }}
          >
            <MenuItem value="">{t("All categories")}</MenuItem>
            {categoryOptions.filter(Boolean).map((option) => (
              <MenuItem key={option} value={option}>
                {translateAdminValue(option, t)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label={t("Priority")}
            value={priority}
            onChange={(event) => {
              updateListState({ priority: event.target.value, page: "" });
            }}
            sx={{ minWidth: { xs: "100%", md: 150 } }}
          >
            <MenuItem value="">{t("All priorities")}</MenuItem>
            {priorityOptions.map((option) => (
              <MenuItem key={option} value={option}>
                {translateAdminValue(option, t)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label={t("Assignee")}
            value={assignee}
            onChange={(event) => {
              updateListState({ assignee: event.target.value, page: "" });
            }}
            sx={{ minWidth: { xs: "100%", md: 180 } }}
          >
            <MenuItem value="">{t("All assignees")}</MenuItem>
            {(usersQuery.data ?? []).map((user) => (
              <MenuItem key={user.userId} value={String(user.userId)}>
                {user.displayName}
              </MenuItem>
            ))}
          </TextField>
        </Stack>

        {ticketsQuery.isFetching && !ticketsQuery.isLoading ? (
          <Alert severity="info">{t("Updating ticket results...")}</Alert>
        ) : null}

        <Box sx={{ overflowX: "auto" }}>
          <Table size="small" aria-label={t("support ticket queue")}>
            <TableHead>
              <TableRow>
                <TableCell>{t("Ticket")}</TableCell>
                <TableCell>{t("Category")}</TableCell>
                <TableCell>{t("Priority")}</TableCell>
                <TableCell>{t("Status")}</TableCell>
                <TableCell>{t("Assignee")}</TableCell>
                <TableCell>{t("Linked record")}</TableCell>
                <TableCell>{t("Updated")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibleTickets.map((ticket) => (
                <TableRow
                  key={ticket.id}
                  hover
                  data-testid={`support-ticket-${ticket.id}`}
                >
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography
                        fontWeight={600}
                        component={RouterLink}
                        to={`/support/tickets/${ticket.id}${location.search}`}
                        sx={{ textDecoration: "none" }}
                      >
                        {ticket.ticketNumber}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {ticket.subject}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    {translateAdminValue(ticket.category, t)}
                  </TableCell>
                  <TableCell>
                    <Chip label={t(ticket.priority)} size="small" />
                  </TableCell>
                  <TableCell>
                    {translateAdminStatus(ticket.status, t)}
                  </TableCell>
                  <TableCell>
                    {ticket.assignedToUserId ?? t("Unassigned")}
                  </TableCell>
                  <TableCell>
                    {ticket.linkedEntityType
                      ? translateAdminValue(ticket.linkedEntityType, t)
                      : "n/a"}
                  </TableCell>
                  <TableCell>
                    {formatEthiopianDateTime(ticket.updatedAt)}
                  </TableCell>
                </TableRow>
              ))}
              {visibleTickets.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Alert severity="info">
                      {t("No tickets match the current filters.")}
                    </Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </Box>

        <AdminTablePagination
          component="div"
          dataLoaded={!!ticketsQuery.data}
          count={ticketsQuery.data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, nextPage) =>
            updateListState({ page: String(nextPage) })
          }
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => {
            updateListState({ size: event.target.value, page: "" });
          }}
          rowsPerPageOptions={[10, 20, 50]}
        />
      </Stack>
    </AdminTablePage>
  );
}
