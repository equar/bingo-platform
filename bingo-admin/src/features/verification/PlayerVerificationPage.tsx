import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useCallback, useState, type FormEvent } from "react";
import { Link as RouterLink, useLocation, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  playerVerificationHistory,
  recordPlayerVerificationReview,
  searchPlayerVerifications,
  type PlayerVerificationQueueItemResponse,
  type PlayerVerificationReason,
  type PlayerVerificationStatus,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { ListRefreshButton, ListRefreshError } from "../../components/ListRefreshButton";
import { AdminTablePage } from "../../components/AdminTablePage";
import { StatusState } from "../../components/StatusState";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { translateAdminStatus } from "../../i18n";

const verificationStatuses: PlayerVerificationStatus[] = [
  "NOT_REVIEWED",
  "IN_REVIEW",
  "VERIFIED",
  "NEEDS_INFORMATION",
  "REJECTED",
];

type ReviewableStatus = Exclude<PlayerVerificationStatus, "NOT_REVIEWED">;

const allowedNextStatuses: Record<PlayerVerificationStatus, ReviewableStatus[]> = {
  NOT_REVIEWED: ["IN_REVIEW"],
  IN_REVIEW: ["VERIFIED", "NEEDS_INFORMATION", "REJECTED"],
  NEEDS_INFORMATION: ["IN_REVIEW"],
  VERIFIED: [],
  REJECTED: [],
};

const reasonOptions: Record<Exclude<PlayerVerificationStatus, "NOT_REVIEWED">, PlayerVerificationReason[]> = {
  IN_REVIEW: ["REVIEW_STARTED"],
  VERIFIED: ["IDENTITY_AND_AGE_CONFIRMED"],
  NEEDS_INFORMATION: ["INFORMATION_REQUIRED"],
  REJECTED: ["IDENTITY_MISMATCH", "AGE_NOT_VERIFIED", "OTHER"],
};

const statusLabels: Record<PlayerVerificationStatus, string> = {
  NOT_REVIEWED: "Not reviewed",
  IN_REVIEW: "In review",
  VERIFIED: "Verified",
  NEEDS_INFORMATION: "Needs information",
  REJECTED: "Rejected",
};

const reasonLabels: Record<PlayerVerificationReason, string> = {
  REVIEW_STARTED: "Review started",
  IDENTITY_AND_AGE_CONFIRMED: "Identity and age confirmed",
  INFORMATION_REQUIRED: "More information required",
  IDENTITY_MISMATCH: "Identity details did not match",
  AGE_NOT_VERIFIED: "Age was not confirmed",
  OTHER: "Other",
};

function statusColor(status: PlayerVerificationStatus) {
  switch (status) {
    case "VERIFIED":
      return "success";
    case "REJECTED":
      return "error";
    case "IN_REVIEW":
    case "NEEDS_INFORMATION":
      return "warning";
    default:
      return "default";
  }
}

function formatDate(value: string | null, locale: string, fallback: string) {
  return value ? new Date(value).toLocaleString(locale) : fallback;
}

export function PlayerVerificationPage() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = usePrivateListSearch("player-verification");
  const statusValue = searchParams.get("status") ?? "";
  const statusFilter = verificationStatuses.includes(statusValue as PlayerVerificationStatus) ? statusValue as PlayerVerificationStatus : "";
  const pageValue = Number(searchParams.get("page") ?? 0);
  const page = Number.isSafeInteger(pageValue) ? Math.max(0, pageValue) : 0;
  const sizeValue = Number(searchParams.get("size") ?? 20);
  const pageSize = [10, 20, 50].includes(sizeValue) ? sizeValue : 20;
  const updateListState = useCallback((updates: Record<string, string>) => setSearchParams((current) => {
    const next = new URLSearchParams(current);
    Object.entries(updates).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    return next;
  }, { replace: true }), [setSearchParams]);
  const [selectedPlayer, setSelectedPlayer] = useState<PlayerVerificationQueueItemResponse | null>(null);
  const [draftStatus, setDraftStatus] = useState<Exclude<PlayerVerificationStatus, "NOT_REVIEWED">>("IN_REVIEW");
  const [draftReason, setDraftReason] = useState<PlayerVerificationReason>("REVIEW_STARTED");
  const [successOpen, setSuccessOpen] = useState(false);

  const queueQuery = useQuery({
    queryKey: ["player-verification-queue", query.trim(), statusFilter, page, pageSize],
    queryFn: () => searchPlayerVerifications(query.trim() || undefined, statusFilter || undefined, page, pageSize),
    placeholderData: (previousData) => previousData,
  });
  const historyQuery = useQuery({
    queryKey: ["player-verification-history", selectedPlayer?.playerId],
    queryFn: () => playerVerificationHistory(selectedPlayer!.playerId),
    enabled: selectedPlayer !== null,
  });
  const reviewMutation = useMutation({
    mutationFn: () => {
      if (!selectedPlayer) {
        throw new Error("Select a player before recording a verification review.");
      }
      return recordPlayerVerificationReview(selectedPlayer.playerId, {
        status: draftStatus,
        reason: draftReason,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["player-verification-queue"] });
      if (selectedPlayer) {
        await queryClient.invalidateQueries({
          queryKey: ["player-verification-history", selectedPlayer.playerId],
        });
      }
      setSelectedPlayer(null);
      setSuccessOpen(true);
    },
  });

  const locale = i18n.language.startsWith("am") ? "am-ET" : "en-US";
  const players = queueQuery.data?.content ?? [];

  const openReview = (player: PlayerVerificationQueueItemResponse) => {
    const nextStatus = allowedNextStatuses[player.verificationStatus][0];
    if (!nextStatus) return;
    setDraftStatus(nextStatus);
    setDraftReason(player.verificationReason ?? reasonOptions[nextStatus][0]);
    reviewMutation.reset();
    setSelectedPlayer(player);
  };

  const submitReview = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    reviewMutation.mutate();
  };

  return (
    <AdminTablePage>
    <Stack spacing={3}>
      <AdminPageHeader title={t("Player verification")} description={t("Track player verification outcomes and review history.")} refresh={{ isFetching: queueQuery.isFetching, failed: queueQuery.isRefetchError, error: queueQuery.error, onRefresh: () => queueQuery.refetch() }} />

      <Alert severity="info">
        {t("Track checks performed using your approved process. This screen stores review outcomes only; do not enter or upload identity documents. Verification status does not automatically restrict play or withdrawals.")}
      </Alert>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label={t("Search by player name, phone, or ID")}
          value={query}
          onChange={(event) => {
                setQuery(event.target.value);
                updateListState({ page: "" });
          }}
          fullWidth
        />
        <TextField
          select
          label={t("Filter by verification status")}
          value={statusFilter}
          onChange={(event) => {
                updateListState({ status: event.target.value, page: "" });
          }}
          sx={{ minWidth: { xs: "100%", md: 250 } }}
        >
          <MenuItem value="">{t("All statuses")}</MenuItem>
          {verificationStatuses.map((status) => (
            <MenuItem key={status} value={status}>{t(statusLabels[status])}</MenuItem>
          ))}
        </TextField>
      </Stack>

      {queueQuery.isLoading ? (
        <StatusState title={t("Loading verification queue")} loading />
      ) : null}
      {queueQuery.error && !queueQuery.data ? (
        <StatusState
          title={t("Unable to load verification queue")}
          message={t(getUserFacingErrorMessage(queueQuery.error) ?? "Something went wrong. Please try again.")}
          actionLabel={t("Retry")}
          onAction={() => queueQuery.refetch()}
        />
      ) : null}
      {queueQuery.isFetching && !queueQuery.isLoading ? (
        <Alert severity="info">{t("Updating player results...")}</Alert>
      ) : null}

      {queueQuery.data ? (
        <>
          <Box sx={{ width: "100%", minWidth: 0, overflowX: "auto" }}>
            <Table size="small" aria-label={t("Player verification queue")} sx={{ minWidth: 760 }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t("Player")}</TableCell>
                  <TableCell>{t("Phone")}</TableCell>
                  <TableCell>{t("Account status")}</TableCell>
                  <TableCell>{t("Verification status")}</TableCell>
                  <TableCell>{t("Last reviewed")}</TableCell>
                  <TableCell align="right">{t("Actions")}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {players.map((player) => (
                  <TableRow key={player.playerId} hover>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography
                          component={RouterLink}
                          to={`/players/${player.playerId}${location.search}`}
                          fontWeight={600}
                        >
                          {player.displayName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {t("Player ID")}: {player.playerId}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{player.phoneNumber}</TableCell>
                    <TableCell>{translateAdminStatus(player.accountStatus, t)}</TableCell>
                    <TableCell>
                      <Chip
                        label={t(statusLabels[player.verificationStatus])}
                        color={statusColor(player.verificationStatus)}
                        size="small"
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      {formatDate(player.reviewedAt, locale, t("Not reviewed"))}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => openReview(player)}
                        disabled={allowedNextStatuses[player.verificationStatus].length === 0}
                      >
                        {allowedNextStatuses[player.verificationStatus].length > 0
                          ? t("Review player")
                          : t("Review closed")}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {queueQuery.data && players.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Alert severity="info">{t("No players match the current search and filters.")}</Alert>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </Box>
          <AdminTablePagination
            component="div"
            dataLoaded={!!queueQuery.data}
            count={queueQuery.data?.totalElements ?? 0}
            page={page}
            onPageChange={(_, nextPage) => updateListState({ page: String(nextPage) })}
            rowsPerPage={pageSize}
            onRowsPerPageChange={(event) => {
              updateListState({ size: event.target.value, page: "" });
            }}
            rowsPerPageOptions={[10, 20, 50]}
          />
        </>
      ) : null}

      <Dialog
        open={selectedPlayer !== null}
        onClose={() => {
          if (!reviewMutation.isPending) {
            setSelectedPlayer(null);
          }
        }}
        fullWidth
        maxWidth="sm"
        aria-labelledby="verification-review-title"
      >
        <Box component="form" onSubmit={submitReview}>
          <DialogTitle id="verification-review-title">
            {t("Record verification review")}
            {selectedPlayer ? ` — ${selectedPlayer.displayName}` : ""}
          </DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info">{t("This records the result of checks completed outside this app.")}</Alert>
              {reviewMutation.isError ? (
                <Alert severity="error">
                  {t(getUserFacingErrorMessage(reviewMutation.error) ?? "Could not record review")}
                </Alert>
              ) : null}
              <FormControl fullWidth required>
                <InputLabel id="verification-status-label">{t("Choose status")}</InputLabel>
                <Select
                  labelId="verification-status-label"
                  label={t("Choose status")}
                  value={draftStatus}
                  onChange={(event) => {
                    const nextStatus = event.target.value as Exclude<PlayerVerificationStatus, "NOT_REVIEWED">;
                    setDraftStatus(nextStatus);
                    setDraftReason(reasonOptions[nextStatus][0]);
                  }}
                >
                  {selectedPlayer && allowedNextStatuses[selectedPlayer.verificationStatus].map((status) => (
                    <MenuItem key={status} value={status}>{t(statusLabels[status])}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl fullWidth required>
                <InputLabel id="verification-reason-label">{t("Choose a reason")}</InputLabel>
                <Select
                  labelId="verification-reason-label"
                  label={t("Choose a reason")}
                  value={draftReason}
                  onChange={(event) => setDraftReason(event.target.value as PlayerVerificationReason)}
                >
                  {reasonOptions[draftStatus].map((reason) => (
                    <MenuItem key={reason} value={reason}>{t(reasonLabels[reason])}</MenuItem>
                  ))}
                </Select>
              </FormControl>

              <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1}>
                <Typography variant="subtitle2">{t("Verification history")}</Typography>
                <ListRefreshButton refreshing={historyQuery.isFetching} onRefresh={() => historyQuery.refetch()} />
              </Stack>
              {historyQuery.isLoading ? (
                <Typography color="text.secondary">{t("Loading...")}</Typography>
              ) : null}
              {historyQuery.error && !historyQuery.data ? (
                <Alert
                  severity="error"
                  action={<Button color="inherit" size="small" onClick={() => historyQuery.refetch()}>{t("Retry")}</Button>}
                >
                  {t(getUserFacingErrorMessage(historyQuery.error) ?? "Unable to load review history")}
                </Alert>
              ) : null}
              {historyQuery.isRefetchError ? <ListRefreshError retrying={historyQuery.isFetching} error={historyQuery.error} onRetry={() => historyQuery.refetch()} /> : null}
              {historyQuery.data?.length ? historyQuery.data.map((review) => (
                <Box key={review.id} sx={{ borderBottom: "1px solid", borderColor: "divider", pb: 1 }}>
                  <Typography>
                    {t(statusLabels[review.status])} — {t(reasonLabels[review.reason])}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatDate(review.reviewedAt, locale, "")} · {t("Reviewed by admin #{{id}}", { id: review.reviewerUserId })}
                  </Typography>
                </Box>
              )) : null}
              {historyQuery.data?.length === 0 ? (
                <Typography color="text.secondary">{t("No review history")}</Typography>
              ) : null}
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setSelectedPlayer(null)} disabled={reviewMutation.isPending}>
              {t("Cancel")}
            </Button>
            <Button type="submit" variant="contained" disabled={reviewMutation.isPending}>
              {reviewMutation.isPending ? t("Saving...") : t("Save review")}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      <Snackbar open={successOpen} autoHideDuration={5000} onClose={() => setSuccessOpen(false)}>
        <Alert severity="success" onClose={() => setSuccessOpen(false)}>{t("Review recorded")}</Alert>
      </Snackbar>
    </Stack>
    </AdminTablePage>
  );
}
