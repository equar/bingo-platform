import { useTranslation } from "react-i18next";
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
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus } from "../../i18n";

const statusOptions = ["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"] as const;

export function PlayersPage() {
  const { t } = useTranslation();
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
        <Typography variant="h4">{t("Players")}</Typography>
        <Typography color="text.secondary">{t("Search users and review account details.")}</Typography>
      </Box>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label={t("Search by name, email, or phone")}
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
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: { xs: "100%", md: 220 } }}
        >
          <MenuItem value="">{t("All statuses")}</MenuItem>
          {statusOptions.map((status) => (
            <MenuItem key={status} value={status}>
              {translateAdminStatus(status, t)}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {playersQuery.isLoading ? <StatusState title={t("Loading players")} loading /> : null}
      {playersQuery.error ? (
        <StatusState
          title={t("Unable to load players")}
          message={t(getUserFacingErrorMessage(playersQuery.error) ?? "Something went wrong. Please try again.")}
          actionLabel={t("Retry")}
          onAction={() => playersQuery.refetch()}
        />
      ) : null}
      {playersQuery.isFetching && !playersQuery.isLoading ? (
        <Alert severity="info">{t("Updating player results...")}</Alert>
      ) : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label={t("players table")}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Player")}</TableCell>
              <TableCell>{t("Phone")}</TableCell>
              <TableCell>{t("Status")}</TableCell>
              <TableCell align="right">{t("Wallet transactions")}</TableCell>
              <TableCell align="right">{t("Deposits")}</TableCell>
              <TableCell align="right">{t("Support")}</TableCell>
              <TableCell>{t("Joined")}</TableCell>
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
                    label={translateAdminStatus(player.status, t)}
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
                  <Alert severity="info">{t("No players match the current search and filters.")}</Alert>
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
