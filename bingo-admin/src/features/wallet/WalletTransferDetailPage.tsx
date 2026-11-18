import { useQuery } from "@tanstack/react-query";
import { Alert, Box, Button, Card, CardContent, Chip, Divider, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { walletTransferDetail } from "../../api/admin";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { AdminTablePage } from "../../components/AdminTablePage";
import { ListRefreshButton, ListRefreshError } from "../../components/ListRefreshButton";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus, translateAdminValue } from "../../i18n";
import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";

export function WalletTransferDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { transferId = "" } = useParams();
  const id = Number(transferId);
  const validId = Number.isInteger(id) && id > 0;
  const detailQuery = useQuery({
    queryKey: ["wallet-transfer-detail", id],
    queryFn: () => walletTransferDetail(id),
    enabled: validId,
  });

  if (!validId) {
    return <StatusState title={t("Unable to load transfers")} message={t("Transfer details could not be loaded. Please try again.")} actionLabel={t("Back to transfers")} onAction={() => navigate("/wallet/transfers")} />;
  }
  if (detailQuery.isLoading) {
    return <StatusState title={t("Loading transfer detail")} loading message={t("Fetching transfer and ledger details")} />;
  }
  if (!detailQuery.data) {
    return <StatusState title={t("Unable to load transfers")} message={t("Transfer details could not be loaded. Please try again.")} actionLabel={t("Retry")} onAction={() => detailQuery.refetch()} />;
  }

  const { transfer, ledgerEntries } = detailQuery.data;
  const details: Array<[string, string]> = [
    [t("Operation"), transfer.operationId],
    [t("Sender"), `${detailQuery.data.senderDisplayName} · ${transfer.senderPhoneNumber}`],
    [t("Receiver"), `${detailQuery.data.recipientDisplayName} · ${transfer.recipientPhoneNumber}`],
    [t("Amount"), `${transfer.amount} ${transfer.currency}`],
    [t("Status"), translateAdminStatus(transfer.status, t)],
    [t("Created"), formatEthiopianDateTime(transfer.createdAt)],
    [t("Completed"), transfer.completedAt ? formatEthiopianDateTime(transfer.completedAt) : t("Pending")],
    [t("Sender before/after"), `${transfer.senderBalanceBefore} → ${transfer.senderBalanceAfter}`],
    [t("Recipient before/after"), `${transfer.recipientBalanceBefore} → ${transfer.recipientBalanceAfter}`],
    [t("Note"), transfer.note ?? t("None")],
    [t("Failure reason"), transfer.failureReason ?? t("No failure reason")],
  ];

  return (
    <AdminTablePage>
      <Stack spacing={2}>
        <AdminPageHeader title={`${t("Transfer detail")} #${transfer.id}`} description={t("Review the sender, receiver, and wallet ledger entries for this transfer.")} />
        <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1}>
          <Button onClick={() => navigate("/wallet/transfers")} sx={{ alignSelf: "flex-start" }}>← {t("Back to transfers")}</Button>
          <ListRefreshButton refreshing={detailQuery.isFetching} onRefresh={() => detailQuery.refetch()} />
        </Stack>
        {detailQuery.isRefetchError ? <ListRefreshError retrying={detailQuery.isFetching} error={detailQuery.error} onRetry={() => detailQuery.refetch()} /> : null}
        <Card elevation={0} sx={{ border: 1, borderColor: "divider", borderRadius: 2 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
              <Typography variant="h6">{t("Transfer details")}</Typography>
              <Chip label={translateAdminStatus(transfer.status, t)} size="small" color={transfer.status === "FAILED" ? "error" : "success"} />
            </Stack>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, columnGap: 3, rowGap: 2 }}>
              {details.map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Typography variant="body2" sx={{ overflowWrap: "anywhere", fontWeight: 500 }}>{value}</Typography>
              </Box>)}
            </Box>
          </CardContent>
        </Card>
        <Card elevation={0} sx={{ border: 1, borderColor: "divider", borderRadius: 2 }}>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1.5 }}>{t("Related wallet ledger entries")}</Typography>
            {ledgerEntries.length ? <Box sx={{ overflowX: "auto" }}>
              <Table size="small" aria-label={t("transfer ledger entries")}>
                <TableHead><TableRow>
                  <TableCell>{t("Entry")}</TableCell><TableCell>{t("Wallet owner")}</TableCell><TableCell>{t("Type")}</TableCell><TableCell align="right">{t("Amount")}</TableCell><TableCell>{t("Balance before → after")}</TableCell>
                </TableRow></TableHead>
                <TableBody>{ledgerEntries.map((entry) => <TableRow key={entry.id} data-testid={`wallet-transfer-ledger-${entry.id}`}>
                  <TableCell>{entry.id}</TableCell><TableCell>{entry.walletOwnerDisplayName}</TableCell><TableCell>{translateAdminValue(entry.transactionType, t)}</TableCell><TableCell align="right">{entry.amount} {entry.currency}</TableCell><TableCell>{entry.balanceBefore} → {entry.balanceAfter}</TableCell>
                </TableRow>)}</TableBody>
              </Table>
            </Box> : <Alert severity="warning" data-testid="wallet-transfer-ledger-missing">{t("No linked wallet ledger entries were found for this transfer.")}</Alert>}
          </CardContent>
        </Card>
      </Stack>
    </AdminTablePage>
  );
}
