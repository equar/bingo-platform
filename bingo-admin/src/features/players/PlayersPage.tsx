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
import { searchPlayers } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

const statusOptions = ["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"] as const;

export function PlayersPage() {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const normalizedQuery = query.trim();
  const playersQuery = useQuery({
    queryKey: ["players", normalizedQuery, statusFilter, page, pageSize],
    queryFn: () => searchPlayers(normalizedQuery, page, pageSize, statusFilter || undefined),
    placeholderData: keepPreviousData,
  });

  const players = playersQuery.data?.content ?? [];

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Players</Typography>
        <Typography color="text.secondary">Search users and review account details.</Typography>
      </Box>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label="Search by name, email, or phone"
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
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 220 } }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {statusOptions.map((status) => (
            <MenuItem key={status} value={status}>
              {status}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {playersQuery.isLoading ? <StatusState title="Loading players" loading /> : null}
      {playersQuery.error ? (
        <StatusState
          title="Unable to load players"
          message={(playersQuery.error as Error).message}
          actionLabel="Retry"
          onAction={() => playersQuery.refetch()}
        />
      ) : null}
      {playersQuery.isFetching && !playersQuery.isLoading ? (
        <Alert severity="info">Updating player results...</Alert>
      ) : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label="players table">
          <TableHead>
            <TableRow>
              <TableCell>Player</TableCell>
              <TableCell>Phone</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Wallet transactions</TableCell>
              <TableCell align="right">Deposits</TableCell>
              <TableCell align="right">Support</TableCell>
              <TableCell>Joined</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {players.map((player) => (
              <TableRow key={player.id} hover>
                <TableCell>
                  <Typography component={RouterLink} to={`/players/${player.id}`} fontWeight={600}>
                    {player.displayName}
                  </Typography>
                </TableCell>
                <TableCell>{player.normalizedPhone}</TableCell>
                <TableCell>
                  <Chip
                    label={player.status}
                    size="small"
                    color={player.status === "ACTIVE" ? "success" : "default"}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell align="right">{player.walletTransactionCount}</TableCell>
                <TableCell align="right">{player.depositClaimCount}</TableCell>
                <TableCell align="right">{player.supportTicketCount}</TableCell>
                <TableCell>{new Date(player.createdAt).toLocaleDateString()}</TableCell>
              </TableRow>
            ))}
            {playersQuery.data && players.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7}>
                  <Alert severity="info">No players match the current search and filters.</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Box>

      <TablePagination
        component="div"
        count={playersQuery.data?.totalElements ?? 0}
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
