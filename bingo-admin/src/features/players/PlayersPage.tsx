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
import { Link as RouterLink, useLocation, useSearchParams } from "react-router-dom";
import { searchPlayers } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { translateAdminStatus } from "../../i18n";

const statusOptions = ["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"] as const;

export function PlayersPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("players");
  const statusParam = searchParams.get("status") ?? "";
  const statusFilter = statusOptions.includes(statusParam as (typeof statusOptions)[number]) ? statusParam : "";
  const pageParam = Number(searchParams.get("page") ?? 0);
  const page = Number.isSafeInteger(pageParam) ? Math.max(0, pageParam) : 0;
  const pageSizeValue = Number(searchParams.get("size") ?? 20);
  const pageSize = [10, 20, 50].includes(pageSizeValue) ? pageSizeValue : 20;
  const updateListState = (updates: Record<string, string>) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      Object.entries(updates).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
      return next;
    }, { replace: true });
  };
  useEffect(() => {
    if (!searchParams.has("search")) return;
    const safeParams = new URLSearchParams(searchParams);
    safeParams.delete("search");
    setSearchParams(safeParams, { replace: true });
  }, [searchParams, setSearchParams]);
  const normalizedQuery = query.trim();
  const playersQuery = useQuery({
    queryKey: ["players", normalizedQuery, statusFilter, page, pageSize],
    queryFn: () => searchPlayers(normalizedQuery, page, pageSize, statusFilter || undefined),
    placeholderData: keepPreviousData,
  });

  const players = playersQuery.data?.content ?? [];

  return (
    <AdminTablePage>
    <Stack spacing={3}>
      <AdminPageHeader title={t("Players")} description={t("Search users and review account details.")} refresh={{ isFetching: playersQuery.isFetching, failed: playersQuery.isRefetchError, error: playersQuery.error, onRefresh: () => playersQuery.refetch() }} />

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label={t("Search by name, email, or phone")}
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
          value={statusFilter}
          onChange={(event) => {
            updateListState({ status: event.target.value, page: "" });
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
      {playersQuery.error && !playersQuery.data ? (
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

      <Box sx={{ width: "100%", minWidth: 0, overflowX: "auto" }}>
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
                  <Typography component={RouterLink} to={`/players/${player.id}${location.search}`} fontWeight={600}>
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

      <AdminTablePagination
        component="div"
        dataLoaded={!!playersQuery.data}
        count={playersQuery.data?.totalElements ?? 0}
        page={page}
        onPageChange={(_, nextPage) => updateListState({ page: String(nextPage) })}
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
