import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
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
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { adminUsers, supportTickets } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

const statusOptions = ["", "OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"];
const categoryOptions = ["", "ACCOUNT", "WALLET", "GAME", "AGENT", "TECHNICAL", "BILLING", "OTHER"];
const priorityOptions = ["LOW", "NORMAL", "HIGH", "URGENT"];

export function SupportTicketsPage() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [priority, setPriority] = useState("");
  const [assignee, setAssignee] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const normalizedQuery = query.trim();
  const usersQuery = useQuery({ queryKey: ["admin-users"], queryFn: () => adminUsers(0, 100) });
  const ticketsQuery = useQuery({
    queryKey: ["support-tickets", normalizedQuery, status, category, priority, assignee, page, pageSize],
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
    return <StatusState title="Loading support tickets" loading message="Fetching call-center queue" />;
  }

  if (ticketsQuery.error) {
    return (
      <StatusState
        title="Unable to load support tickets"
        message={(ticketsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => ticketsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Support tickets</Typography>
        <Typography color="text.secondary">
          Queue, filter, and review call-center tickets with public and internal notes kept separate.
        </Typography>
      </Box>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label="Search tickets, subjects, players, or phone"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          fullWidth
        />
        <TextField
          select
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 200 } }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {statusOptions.filter(Boolean).map((option) => (
            <MenuItem key={option} value={option}>
              {option.replace(/_/g, " ")}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Category"
          value={category}
          onChange={(event) => {
            setCategory(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 180 } }}
        >
          <MenuItem value="">All categories</MenuItem>
          {categoryOptions.filter(Boolean).map((option) => (
            <MenuItem key={option} value={option}>
              {option}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Priority"
          value={priority}
          onChange={(event) => {
            setPriority(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 150 } }}
        >
          <MenuItem value="">All priorities</MenuItem>
          {priorityOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {option}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Assignee"
          value={assignee}
          onChange={(event) => {
            setAssignee(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 180 } }}
        >
          <MenuItem value="">All assignees</MenuItem>
          {(usersQuery.data?.content ?? []).map((user) => (
            <MenuItem key={user.id} value={String(user.id)}>
              {user.displayName}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {ticketsQuery.isFetching && !ticketsQuery.isLoading ? (
        <Alert severity="info">Updating ticket results...</Alert>
      ) : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label="support ticket queue">
            <TableHead>
              <TableRow>
                <TableCell>Ticket</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Assignee</TableCell>
                <TableCell>Linked record</TableCell>
                <TableCell>Updated</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibleTickets.map((ticket) => (
                <TableRow key={ticket.id} hover data-testid={`support-ticket-${ticket.id}`}>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography
                        fontWeight={600}
                        component={RouterLink}
                        to={`/support/tickets/${ticket.id}`}
                        sx={{ textDecoration: "none" }}
                      >
                        {ticket.ticketNumber}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {ticket.subject}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{ticket.category}</TableCell>
                  <TableCell>
                    <Chip label={ticket.priority} size="small" />
                  </TableCell>
                  <TableCell>{ticket.status}</TableCell>
                  <TableCell>{ticket.assignedToUserId ?? "Unassigned"}</TableCell>
                  <TableCell>{ticket.linkedEntityType ?? "n/a"}</TableCell>
                  <TableCell>{new Date(ticket.updatedAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
              {visibleTickets.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Alert severity="info">No tickets match the current filters.</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
        </Table>
      </Box>

      <TablePagination
        component="div"
        count={ticketsQuery.data?.totalElements ?? 0}
        page={page}
        onPageChange={(_, nextPage) => setPage(nextPage)}
        rowsPerPage={pageSize}
        onRowsPerPageChange={(event) => {
          setPageSize(Number(event.target.value));
          setPage(0);
        }}
        rowsPerPageOptions={[10, 20, 50]}
      />
    </Stack>
  );
}
