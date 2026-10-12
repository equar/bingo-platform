import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
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
import { Link as RouterLink } from "react-router-dom";
import { adminUsers, supportTickets } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

const statusOptions = ["", "OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"];
const categoryOptions = ["", "ACCOUNT", "WALLET", "GAME", "AGENT", "TECHNICAL", "BILLING", "OTHER"];

function countByPriority(items: { priority: string }[]) {
  return items.reduce<Record<string, number>>((accumulator, item) => {
    accumulator[item.priority] = (accumulator[item.priority] ?? 0) + 1;
    return accumulator;
  }, {});
}

export function SupportTicketsPage() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [assignee, setAssignee] = useState("");
  const usersQuery = useQuery({ queryKey: ["admin-users"], queryFn: () => adminUsers(0, 100) });
  const ticketsQuery = useQuery({
    queryKey: ["support-tickets", query, status, category],
    queryFn: () => supportTickets(query || undefined, status || undefined, category || undefined, 0, 100),
  });

  const visibleTickets = useMemo(() => {
    const items = ticketsQuery.data?.content ?? [];
    if (!assignee) {
      return items;
    }
    return items.filter((ticket) => String(ticket.assignedToUserId ?? "") === assignee);
  }, [assignee, ticketsQuery.data]);

  const priorityCounts = countByPriority(visibleTickets);

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

      <Alert severity="warning">
        The backend supports status and category filtering server-side, but assignee filtering and priority rollups are
        not exposed as aggregate endpoints. Those parts are filtered from the loaded page in the UI.
      </Alert>

      <Grid container spacing={2}>
        {["LOW", "NORMAL", "HIGH", "URGENT"].map((priority) => (
          <Grid item xs={12} sm={6} md={3} key={priority}>
            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">
                  {priority}
                </Typography>
                <Typography variant="h4">{priorityCounts[priority] ?? 0}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <TextField label="Search" value={query} onChange={(event) => setQuery(event.target.value)} fullWidth />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField select label="Status" value={status} onChange={(event) => setStatus(event.target.value)} fullWidth>
            {statusOptions.map((option) => (
              <MenuItem key={option || "all"} value={option}>
                {option || "All statuses"}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField
            select
            label="Category"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            fullWidth
          >
            {categoryOptions.map((option) => (
              <MenuItem key={option || "all"} value={option}>
                {option || "All categories"}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField
            select
            label="Assignee"
            value={assignee}
            onChange={(event) => setAssignee(event.target.value)}
            fullWidth
          >
            <MenuItem value="">All assignees</MenuItem>
            {(usersQuery.data?.content ?? []).map((user) => (
              <MenuItem key={user.id} value={String(user.id)}>
                {user.displayName}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
      </Grid>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent sx={{ p: 0 }}>
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
                <TableRow key={ticket.id} hover>
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
        </CardContent>
      </Card>
    </Stack>
  );
}
