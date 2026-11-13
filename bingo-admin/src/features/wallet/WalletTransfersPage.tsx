import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
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
import { walletTransferDetail, walletTransfers } from "../../api/admin";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import {
  ListRefreshButton,
  ListRefreshError,
} from "../../components/ListRefreshButton";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { translateAdminValue } from "../../i18n";
import { translateAdminStatus } from "../../i18n";

export function WalletTransfersPage() {
  const { t } = useTranslation();
  const [query, setQuery] = usePrivateListSearch("wallet-transfers");
  const [status, setStatus] = useListSessionState(
    "wallet-transfers:status",
    "",
  );
  const [senderPhone, setSenderPhone] = usePrivateListSearch(
    "wallet-transfers-sender",
  );
  const [recipientPhone, setRecipientPhone] = usePrivateListSearch(
    "wallet-transfers-recipient",
  );
  const [page, setPage] = useListSessionState("wallet-transfers:page", 0);
  const [pageSize, setPageSize] = useListSessionState(
    "wallet-transfers:size",
    20,
  );
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const normalizedQuery = query.trim();
  const normalizedSenderPhone = senderPhone.trim();
  const normalizedRecipientPhone = recipientPhone.trim();

  const transfersQuery = useQuery({
    queryKey: [
      "wallet-transfers",
      normalizedQuery,
      status,
      normalizedSenderPhone,
      normalizedRecipientPhone,
      page,
      pageSize,
    ],
    queryFn: () =>
      walletTransfers(
        normalizedQuery || undefined,
        status.trim() || undefined,
        normalizedSenderPhone || undefined,
        normalizedRecipientPhone || undefined,
        page,
        pageSize,
      ),
    placeholderData: keepPreviousData,
  });

  const transferDetailQuery = useQuery({
    queryKey: ["wallet-transfer-detail", selectedId],
    queryFn: () => walletTransferDetail(selectedId as number),
    enabled: selectedId !== null,
  });

  const selectedTransfer = transferDetailQuery.data?.transfer ?? null;

  const refreshTransfers = async () => {
    const refreshed = await transfersQuery.refetch();
    if (selectedId === null) return refreshed;
    const stillListed =
      refreshed.data?.content.some((transfer) => transfer.id === selectedId) ??
      false;
    if (!stillListed) {
      setSelectedId(null);
      return refreshed;
    }
    await transferDetailQuery.refetch();
    return refreshed;
  };

  if (transfersQuery.isLoading) {
    return (
      <StatusState
        title={t("Loading transfers")}
        loading
        message={t("Fetching player transfer history")}
      />
    );
  }

  if (transfersQuery.error && !transfersQuery.data) {
    return (
      <StatusState
        title={t("Unable to load transfers")}
        message={t("Transfer history could not be loaded. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => transfersQuery.refetch()}
      />
    );
  }

  return (
    <AdminTablePage>
      <Stack spacing={3}>
        <AdminPageHeader
          title={t("Transfer history")}
          description={t(
            "Search player-to-player wallet transfers by sender, recipient, or note.",
          )}
          refresh={{
            isFetching:
              transfersQuery.isFetching || transferDetailQuery.isFetching,
            failed:
              transfersQuery.isRefetchError ||
              transferDetailQuery.isRefetchError,
            error: transfersQuery.error ?? transferDetailQuery.error,
            onRefresh: refreshTransfers,
          }}
        />

        <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
          <TextField
            label={t("Search sender, recipient, note, or operation")}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
              setSelectedId(null);
            }}
            fullWidth
          />
          <TextField
            select
            label={t("Status")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
              setSelectedId(null);
            }}
            sx={{ minWidth: { xs: "100%", lg: 180 } }}
          >
            <MenuItem value="">{t("All statuses")}</MenuItem>
            <MenuItem value="COMPLETED">{t("Completed")}</MenuItem>
            <MenuItem value="FAILED">{t("Failed")}</MenuItem>
          </TextField>
          <TextField
            label={t("Sender phone")}
            value={senderPhone}
            onChange={(event) => {
              setSenderPhone(event.target.value);
              setPage(0);
              setSelectedId(null);
            }}
            fullWidth
          />
          <TextField
            label={t("Recipient phone")}
            value={recipientPhone}
            onChange={(event) => {
              setRecipientPhone(event.target.value);
              setPage(0);
              setSelectedId(null);
            }}
            fullWidth
          />
        </Stack>

        {transfersQuery.isFetching && !transfersQuery.isLoading ? (
          <Alert severity="info">{t("Updating transfer results...")}</Alert>
        ) : null}

        <Grid container spacing={2}>
          <Grid item xs={12} lg={7}>
            <Box
              sx={{
                overflowX: "auto",
                border: 1,
                borderColor: "divider",
                borderRadius: 1,
              }}
            >
              <Table size="small" aria-label={t("wallet transfers table")}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t("Sender / recipient")}</TableCell>
                    <TableCell align="right">{t("Amount")}</TableCell>
                    <TableCell>{t("Status")}</TableCell>
                    <TableCell>{t("Created")}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(transfersQuery.data?.content ?? []).map((transfer) => (
                    <TableRow
                      key={transfer.id}
                      hover
                      selected={selectedId === transfer.id}
                      role="button"
                      tabIndex={0}
                      aria-label={t(
                        "Transfer from {{sender}} to {{recipient}}",
                        {
                          sender: transfer.senderPhoneNumber,
                          recipient: transfer.recipientPhoneNumber,
                        },
                      )}
                      aria-pressed={selectedId === transfer.id}
                      onClick={() => setSelectedId(transfer.id)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          setSelectedId(transfer.id);
                        }
                      }}
                      sx={{ cursor: "pointer" }}
                      data-testid={`wallet-transfer-row-${transfer.id}`}
                    >
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography fontWeight={600}>
                            {transfer.senderPhoneNumber}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {t("To")} {transfer.recipientPhoneNumber}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {transfer.note ?? t("No note")}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                        {transfer.amount} {transfer.currency}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={translateAdminStatus(transfer.status, t)}
                          size="small"
                        />
                      </TableCell>
                      <TableCell sx={{ whiteSpace: "nowrap" }}>
                        {formatEthiopianDateTime(transfer.createdAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                  {transfersQuery.data &&
                  transfersQuery.data.content.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4}>
                        <Alert severity="info">
                          {t(
                            "No transfers match the current search and filters.",
                          )}
                        </Alert>
                      </TableCell>
                    </TableRow>
                  ) : null}
                </TableBody>
              </Table>
            </Box>
            <AdminTablePagination
              component="div"
              dataLoaded={!!transfersQuery.data}
              count={transfersQuery.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, nextPage) => {
                setPage(nextPage);
                setSelectedId(null);
              }}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(event) => {
                setPageSize(Number(event.target.value));
                setPage(0);
                setSelectedId(null);
              }}
              rowsPerPageOptions={[10, 20, 50]}
            />
          </Grid>
          <Grid item xs={12} lg={5}>
            <Card
              elevation={0}
              sx={{
                border: "1px solid",
                borderColor: "divider",
                minHeight: 260,
              }}
            >
              <CardContent>
                {selectedId !== null && transferDetailQuery.isLoading ? (
                  <StatusState
                    title={t("Loading transfer detail")}
                    loading
                    message={t("Fetching transfer and ledger details")}
                  />
                ) : selectedId !== null &&
                  transferDetailQuery.error &&
                  !transferDetailQuery.data ? (
                  <Alert
                    severity="error"
                    data-testid="wallet-transfer-detail-error"
                  >
                    {t(
                      "Transfer details could not be loaded. Please try again.",
                    )}
                  </Alert>
                ) : selectedTransfer ? (
                  <Stack spacing={1.5}>
                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      alignItems="center"
                      gap={1}
                    >
                      <Typography variant="h6">
                        {t("Transfer detail")}
                      </Typography>
                      <ListRefreshButton
                        refreshing={transferDetailQuery.isFetching}
                        onRefresh={() => transferDetailQuery.refetch()}
                      />
                    </Stack>
                    {transferDetailQuery.isRefetchError ? (
                      <ListRefreshError
                        retrying={transferDetailQuery.isFetching}
                        error={transferDetailQuery.error}
                        onRetry={() => transferDetailQuery.refetch()}
                      />
                    ) : null}
                    <Typography>
                      {t("Operation: {{operationId}}", {
                        operationId: selectedTransfer.operationId,
                      })}
                    </Typography>
                    <Typography>
                      {t("Sender:")} {selectedTransfer.senderPhoneNumber}{" "}
                      {t("(")}
                      {selectedTransfer.senderId}
                      {t(")")}
                    </Typography>
                    <Typography>
                      {t("Recipient:")} {selectedTransfer.recipientPhoneNumber}{" "}
                      {t("(")}
                      {selectedTransfer.recipientId}
                      {t(")")}
                    </Typography>
                    <Typography>
                      {t("Amount:")} {selectedTransfer.amount}{" "}
                      {selectedTransfer.currency}
                    </Typography>
                    <Typography>
                      {t("Status:")}{" "}
                      {translateAdminStatus(selectedTransfer.status, t)}
                    </Typography>
                    <Typography>
                      {t("Created:")}{" "}
                      {formatEthiopianDateTime(selectedTransfer.createdAt)}
                    </Typography>
                    <Typography>
                      {t("Completed:")}{" "}
                      {selectedTransfer.completedAt
                        ? formatEthiopianDateTime(selectedTransfer.completedAt)
                        : "Pending"}
                    </Typography>
                    <Typography>
                      {t("Sender before/after:")}{" "}
                      {selectedTransfer.senderBalanceBefore} {t("→")}{" "}
                      {selectedTransfer.senderBalanceAfter}
                    </Typography>
                    <Typography>
                      {t("Recipient before/after:")}{" "}
                      {selectedTransfer.recipientBalanceBefore} {t("→")}{" "}
                      {selectedTransfer.recipientBalanceAfter}
                    </Typography>
                    <Typography>
                      {t("Note:")} {selectedTransfer.note ?? t("None")}
                    </Typography>
                    <Typography color="text.secondary">
                      {selectedTransfer.failureReason ?? t("No failure reason")}
                    </Typography>
                    <Typography variant="subtitle2">
                      {t("Related wallet ledger entries")}
                    </Typography>
                    {transferDetailQuery.data?.ledgerEntries.length ? (
                      <Table
                        size="small"
                        aria-label={t("transfer ledger entries")}
                      >
                        <TableHead>
                          <TableRow>
                            <TableCell>{t("Entry")}</TableCell>
                            <TableCell>{t("Wallet owner")}</TableCell>
                            <TableCell>{t("Type")}</TableCell>
                            <TableCell align="right">{t("Amount")}</TableCell>
                            <TableCell>{t("Balance before → after")}</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {transferDetailQuery.data.ledgerEntries.map(
                            (entry) => (
                              <TableRow
                                key={entry.id}
                                data-testid={`wallet-transfer-ledger-${entry.id}`}
                              >
                                <TableCell>{entry.id}</TableCell>
                                <TableCell>{entry.walletOwnerId}</TableCell>
                                <TableCell>
                                  {translateAdminValue(
                                    entry.transactionType,
                                    t,
                                  )}
                                </TableCell>
                                <TableCell align="right">
                                  {entry.amount} {entry.currency}
                                </TableCell>
                                <TableCell>
                                  {entry.balanceBefore} {t("→")}{" "}
                                  {entry.balanceAfter}
                                </TableCell>
                              </TableRow>
                            ),
                          )}
                        </TableBody>
                      </Table>
                    ) : (
                      <Alert
                        severity="warning"
                        data-testid="wallet-transfer-ledger-missing"
                      >
                        {t(
                          "No linked wallet ledger entries were found for this transfer.",
                        )}
                      </Alert>
                    )}
                  </Stack>
                ) : (
                  <Typography color="text.secondary">
                    {t("Select a transfer to inspect the details.")}
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Stack>
    </AdminTablePage>
  );
}
