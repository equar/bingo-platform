import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableContainer,
  Tabs,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import {
  playerDeposits,
  playerSupportTickets,
  playerTickets,
  playerWalletTransactions,
  playerWinClaims,
  type DepositClaimResponse,
  type SupportTicketSummaryResponse,
  type TicketResponse,
  type WalletTransactionResponse,
  type WinClaimResponse,
} from "../../api/admin";
import { getUserFacingErrorMessage, type PageResponse } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import {
  ListRefreshButton,
  ListRefreshError,
} from "../../components/ListRefreshButton";
import { translateAdminStatus, translateAdminValue } from "../../i18n";

export type PlayerActivityType =
  "wallet" | "deposits" | "support" | "tickets" | "wins";

interface PlayerActivityDialogProps {
  open: boolean;
  playerId: number;
  initialActivity: PlayerActivityType;
  onClose: () => void;
}

const activityTabs: { value: PlayerActivityType; label: string }[] = [
  { value: "wallet", label: "Wallet" },
  { value: "deposits", label: "Deposits" },
  { value: "support", label: "Support" },
  { value: "tickets", label: "Game tickets" },
  { value: "wins", label: "Win claims" },
];

type PlayerActivityPage =
  | PageResponse<WalletTransactionResponse>
  | PageResponse<DepositClaimResponse>
  | PageResponse<SupportTicketSummaryResponse>
  | PageResponse<TicketResponse>
  | PageResponse<WinClaimResponse>;

export function PlayerActivityDialog({
  open,
  playerId,
  initialActivity,
  onClose,
}: PlayerActivityDialogProps) {
  const { t } = useTranslation();
  const [activity, setActivity] = useState(initialActivity);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const activityQuery = useQuery<PlayerActivityPage>({
    queryKey: ["player-activity", playerId, activity, page, pageSize],
    queryFn: async (): Promise<PlayerActivityPage> => {
      switch (activity) {
        case "wallet":
          return playerWalletTransactions(playerId, page, pageSize);
        case "deposits":
          return playerDeposits(playerId, page, pageSize);
        case "support":
          return playerSupportTickets(playerId, page, pageSize);
        case "tickets":
          return playerTickets(playerId, page, pageSize);
        case "wins":
          return playerWinClaims(playerId, page, pageSize);
      }
    },
    enabled: open,
  });

  const walletRows =
    (activityQuery.data as PageResponse<WalletTransactionResponse> | undefined)
      ?.content ?? [];
  const depositRows =
    (activityQuery.data as PageResponse<DepositClaimResponse> | undefined)
      ?.content ?? [];
  const supportRows =
    (
      activityQuery.data as
        PageResponse<SupportTicketSummaryResponse> | undefined
    )?.content ?? [];
  const ticketRows =
    (activityQuery.data as PageResponse<TicketResponse> | undefined)?.content ??
    [];
  const winRows =
    (activityQuery.data as PageResponse<WinClaimResponse> | undefined)
      ?.content ?? [];
  const totalElements = activityQuery.data?.totalElements ?? 0;
  const label = t(
    activityTabs.find((tab) => tab.value === activity)?.label ?? "Activity",
  );

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="lg"
      aria-labelledby="player-activity-title"
    >
      <DialogTitle id="player-activity-title">
        {label} {t("for Player #")}
        {playerId}
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Tabs
            value={activity}
            onChange={(_, value: PlayerActivityType) => {
              setActivity(value);
              setPage(0);
            }}
            variant="scrollable"
            scrollButtons="auto"
            aria-label={t("Player activity categories")}
          >
            {activityTabs.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={t(tab.label)} />
            ))}
          </Tabs>

          <Stack direction="row" justifyContent="flex-end">
            <ListRefreshButton
              refreshing={activityQuery.isFetching}
              onRefresh={() => activityQuery.refetch()}
            />
          </Stack>

          {activityQuery.isLoading ? (
            <StatusState
              title={t("Loading {{activity}}", {
                activity: label.toLowerCase(),
              })}
              loading
              message={t("Fetching player {{activity}}", {
                activity: label.toLowerCase(),
              })}
            />
          ) : null}
          {activityQuery.error && !activityQuery.data ? (
            <Alert
              severity="error"
              action={
                <Button
                  color="inherit"
                  size="small"
                  onClick={() => activityQuery.refetch()}
                >
                  {t("Retry")}
                </Button>
              }
            >
              {t(
                getUserFacingErrorMessage(activityQuery.error) ??
                  "Unable to load player {{activity}}.",
                { activity: t(label).toLowerCase() },
              )}
            </Alert>
          ) : null}
          {activityQuery.isRefetchError ? (
            <ListRefreshError
              retrying={activityQuery.isFetching}
              error={activityQuery.error}
              onRetry={() => activityQuery.refetch()}
            />
          ) : null}

          {!activityQuery.isLoading && activityQuery.data ? (
            <TableContainer
              component={Paper}
              variant="outlined"
              sx={{ maxHeight: 480 }}
            >
              <Table
                size="small"
                stickyHeader
                aria-label={t("{{activity}} for player {{playerId}}", {
                  activity: label,
                  playerId,
                })}
              >
                {activity === "wallet" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Date")}</TableCell>
                        <TableCell>{t("Type")}</TableCell>
                        <TableCell align="right">{t("Amount")}</TableCell>
                        <TableCell>{t("Balance after")}</TableCell>
                        <TableCell>{t("Reference / note")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {walletRows.map((transaction) => (
                        <TableRow key={transaction.id} hover>
                          <TableCell>
                            {formatEthiopianDateTime(transaction.createdAt)}
                          </TableCell>
                          <TableCell>
                            {translateAdminValue(
                              transaction.transactionType,
                              t,
                            )}
                          </TableCell>
                          <TableCell align="right">
                            {transaction.amount} {transaction.currency}
                          </TableCell>
                          <TableCell>
                            {transaction.balanceAfter} {transaction.currency}
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {transaction.referenceType} {t("#")}
                              {transaction.referenceId ?? "n/a"}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {transaction.note ?? transaction.operationId}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                      {walletRows.length === 0 ? (
                        <EmptyRow colSpan={5} />
                      ) : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "deposits" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Deposit")}</TableCell>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell>{t("Payment reference")}</TableCell>
                        <TableCell align="right">{t("Submitted")}</TableCell>
                        <TableCell align="right">
                          {t("Wallet credit")}
                        </TableCell>
                        <TableCell>{t("Review")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {depositRows.map((deposit) => (
                        <TableRow key={deposit.id} hover>
                          <TableCell>
                            {t("#")}
                            {deposit.id}
                          </TableCell>
                          <TableCell>
                            {translateAdminStatus(deposit.status, t)}
                          </TableCell>
                          <TableCell>
                            {deposit.normalizedTelebirrReference}
                          </TableCell>
                          <TableCell align="right">
                            {deposit.submittedCashAmount} {deposit.currency}
                          </TableCell>
                          <TableCell align="right">
                            {deposit.calculatedCreditAmount} {deposit.currency}
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {deposit.reviewNote ?? t("No review note")}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {deposit.reviewedAt
                                ? formatEthiopianDateTime(deposit.reviewedAt)
                                : t("Not reviewed")}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                      {depositRows.length === 0 ? (
                        <EmptyRow colSpan={6} />
                      ) : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "support" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Ticket")}</TableCell>
                        <TableCell>{t("Subject")}</TableCell>
                        <TableCell>{t("Category")}</TableCell>
                        <TableCell>{t("Priority")}</TableCell>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell>{t("Updated")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {supportRows.map((ticket) => (
                        <TableRow key={ticket.id} hover>
                          <TableCell>
                            <Link
                              component={RouterLink}
                              to={`/support/tickets/${ticket.id}`}
                              underline="hover"
                            >
                              {ticket.ticketNumber}
                            </Link>
                          </TableCell>
                          <TableCell>{ticket.subject}</TableCell>
                          <TableCell>
                            {translateAdminValue(ticket.category, t)}
                          </TableCell>
                          <TableCell>{t(ticket.priority)}</TableCell>
                          <TableCell>
                            {translateAdminStatus(ticket.status, t)}
                          </TableCell>
                          <TableCell>
                            {formatEthiopianDateTime(ticket.updatedAt)}
                          </TableCell>
                        </TableRow>
                      ))}
                      {supportRows.length === 0 ? (
                        <EmptyRow colSpan={6} />
                      ) : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "tickets" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Ticket")}</TableCell>
                        <TableCell>{t("Session")}</TableCell>
                        <TableCell>{t("Public code")}</TableCell>
                        <TableCell>{t("Purchased")}</TableCell>
                        <TableCell>{t("Grid")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {ticketRows.map((ticket) => (
                        <TableRow key={ticket.id} hover>
                          <TableCell>
                            {t("#")}
                            {ticket.id}
                          </TableCell>
                          <TableCell>
                            {t("#")}
                            {ticket.sessionId}
                          </TableCell>
                          <TableCell>{ticket.publicTicketCode}</TableCell>
                          <TableCell>
                            {formatEthiopianDateTime(ticket.purchasedAt)}
                          </TableCell>
                          <TableCell>
                            <Typography
                              variant="caption"
                              sx={{ whiteSpace: "pre-wrap" }}
                            >
                              {ticket.grid
                                .map((row) => row.join(" "))
                                .join(" / ")}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                      {ticketRows.length === 0 ? (
                        <EmptyRow colSpan={5} />
                      ) : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "wins" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t("Claim")}</TableCell>
                        <TableCell>{t("Ticket / session")}</TableCell>
                        <TableCell>{t("Pattern")}</TableCell>
                        <TableCell>{t("Status")}</TableCell>
                        <TableCell>{t("Validation")}</TableCell>
                        <TableCell>{t("Submitted")}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {winRows.map((claim) => (
                        <TableRow key={claim.id} hover>
                          <TableCell>
                            <Typography variant="body2">
                              {t("#")}
                              {claim.id}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {claim.serverValidationCode ??
                                t("No validation code")}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            {t("#")}
                            {claim.ticketId} {t("/ #")}
                            {claim.sessionId}
                          </TableCell>
                          <TableCell>
                            {translateAdminValue(claim.claimedPattern, t)}
                          </TableCell>
                          <TableCell>
                            {translateAdminStatus(claim.status, t)}
                          </TableCell>
                          <TableCell>
                            {claim.serverValidationPassed
                              ? t("Passed")
                              : t("Failed")}
                            {claim.serverValidationMessage ? (
                              <Typography
                                display="block"
                                variant="caption"
                                color="text.secondary"
                              >
                                {claim.serverValidationMessage}
                              </Typography>
                            ) : null}
                          </TableCell>
                          <TableCell>
                            {formatEthiopianDateTime(claim.createdAt)}
                          </TableCell>
                        </TableRow>
                      ))}
                      {winRows.length === 0 ? <EmptyRow colSpan={6} /> : null}
                    </TableBody>
                  </>
                ) : null}
              </Table>
            </TableContainer>
          ) : null}

          {!activityQuery.isLoading && activityQuery.data ? (
            <AdminTablePagination
              component="div"
              dataLoaded={!!activityQuery.data}
              count={totalElements}
              page={page}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(event) => {
                setPageSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 20, 50]}
            />
          ) : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("Close")}</Button>
      </DialogActions>
    </Dialog>
  );
}

function EmptyRow({ colSpan }: { colSpan: number }) {
  const { t } = useTranslation();
  return (
    <TableRow>
      <TableCell colSpan={colSpan}>
        <Box py={2} textAlign="center">
          <Typography color="text.secondary">
            {t("No records for this player.")}
          </Typography>
        </Box>
      </TableCell>
    </TableRow>
  );
}
