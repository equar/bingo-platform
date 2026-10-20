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
  TablePagination,
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
import type { PageResponse } from "../../api/types";
import { StatusState } from "../../components/StatusState";

export type PlayerActivityType = "wallet" | "deposits" | "support" | "tickets" | "wins";

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

export function PlayerActivityDialog({ open, playerId, initialActivity, onClose }: PlayerActivityDialogProps) {
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

  const walletRows = (activityQuery.data as PageResponse<WalletTransactionResponse> | undefined)?.content ?? [];
  const depositRows = (activityQuery.data as PageResponse<DepositClaimResponse> | undefined)?.content ?? [];
  const supportRows = (activityQuery.data as PageResponse<SupportTicketSummaryResponse> | undefined)?.content ?? [];
  const ticketRows = (activityQuery.data as PageResponse<TicketResponse> | undefined)?.content ?? [];
  const winRows = (activityQuery.data as PageResponse<WinClaimResponse> | undefined)?.content ?? [];
  const totalElements = activityQuery.data?.totalElements ?? 0;
  const label = activityTabs.find((tab) => tab.value === activity)?.label ?? "Activity";

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg" aria-labelledby="player-activity-title">
      <DialogTitle id="player-activity-title">
        {label} for Player #{playerId}
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
            aria-label="Player activity categories"
          >
            {activityTabs.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={tab.label} />
            ))}
          </Tabs>

          {activityQuery.isLoading ? (
            <StatusState
              title={`Loading ${label.toLowerCase()}`}
              loading
              message={`Fetching player ${label.toLowerCase()}`}
            />
          ) : null}
          {activityQuery.error ? (
            <Alert
              severity="error"
              action={
                <Button color="inherit" size="small" onClick={() => activityQuery.refetch()}>
                  Retry
                </Button>
              }
            >
              {(activityQuery.error as Error).message || `Unable to load player ${label.toLowerCase()}.`}
            </Alert>
          ) : null}

          {!activityQuery.isLoading && !activityQuery.error ? (
            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 480 }}>
              <Table size="small" stickyHeader aria-label={`${label} for player ${playerId}`}>
                {activity === "wallet" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell align="right">Amount</TableCell>
                        <TableCell>Balance after</TableCell>
                        <TableCell>Reference / note</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {walletRows.map((transaction) => (
                        <TableRow key={transaction.id} hover>
                          <TableCell>{new Date(transaction.createdAt).toLocaleString()}</TableCell>
                          <TableCell>{transaction.transactionType.replace(/_/g, " ")}</TableCell>
                          <TableCell align="right">
                            {transaction.amount} {transaction.currency}
                          </TableCell>
                          <TableCell>
                            {transaction.balanceAfter} {transaction.currency}
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {transaction.referenceType} #{transaction.referenceId ?? "n/a"}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {transaction.note ?? transaction.operationId}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                      {walletRows.length === 0 ? <EmptyRow colSpan={5} /> : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "deposits" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>Deposit</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Payment reference</TableCell>
                        <TableCell align="right">Submitted</TableCell>
                        <TableCell align="right">Wallet credit</TableCell>
                        <TableCell>Review</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {depositRows.map((deposit) => (
                        <TableRow key={deposit.id} hover>
                          <TableCell>#{deposit.id}</TableCell>
                          <TableCell>{deposit.status.replace(/_/g, " ")}</TableCell>
                          <TableCell>{deposit.normalizedTelebirrReference}</TableCell>
                          <TableCell align="right">
                            {deposit.submittedCashAmount} {deposit.currency}
                          </TableCell>
                          <TableCell align="right">
                            {deposit.calculatedCreditAmount} {deposit.currency}
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">{deposit.reviewNote ?? "No review note"}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {deposit.reviewedAt ? new Date(deposit.reviewedAt).toLocaleString() : "Not reviewed"}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                      {depositRows.length === 0 ? <EmptyRow colSpan={6} /> : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "support" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>Ticket</TableCell>
                        <TableCell>Subject</TableCell>
                        <TableCell>Category</TableCell>
                        <TableCell>Priority</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Updated</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {supportRows.map((ticket) => (
                        <TableRow key={ticket.id} hover>
                          <TableCell>
                            <Link component={RouterLink} to={`/support/tickets/${ticket.id}`} underline="hover">
                              {ticket.ticketNumber}
                            </Link>
                          </TableCell>
                          <TableCell>{ticket.subject}</TableCell>
                          <TableCell>{ticket.category}</TableCell>
                          <TableCell>{ticket.priority}</TableCell>
                          <TableCell>{ticket.status.replace(/_/g, " ")}</TableCell>
                          <TableCell>{new Date(ticket.updatedAt).toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                      {supportRows.length === 0 ? <EmptyRow colSpan={6} /> : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "tickets" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>Ticket</TableCell>
                        <TableCell>Session</TableCell>
                        <TableCell>Public code</TableCell>
                        <TableCell>Purchased</TableCell>
                        <TableCell>Grid</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {ticketRows.map((ticket) => (
                        <TableRow key={ticket.id} hover>
                          <TableCell>#{ticket.id}</TableCell>
                          <TableCell>#{ticket.sessionId}</TableCell>
                          <TableCell>{ticket.publicTicketCode}</TableCell>
                          <TableCell>{new Date(ticket.purchasedAt).toLocaleString()}</TableCell>
                          <TableCell>
                            <Typography variant="caption" sx={{ whiteSpace: "pre-wrap" }}>
                              {ticket.grid.map((row) => row.join(" ")).join(" / ")}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ))}
                      {ticketRows.length === 0 ? <EmptyRow colSpan={5} /> : null}
                    </TableBody>
                  </>
                ) : null}

                {activity === "wins" ? (
                  <>
                    <TableHead>
                      <TableRow>
                        <TableCell>Claim</TableCell>
                        <TableCell>Ticket / session</TableCell>
                        <TableCell>Pattern</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Validation</TableCell>
                        <TableCell>Submitted</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {winRows.map((claim) => (
                        <TableRow key={claim.id} hover>
                          <TableCell>
                            <Typography variant="body2">#{claim.id}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {claim.serverValidationCode ?? "No validation code"}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            #{claim.ticketId} / #{claim.sessionId}
                          </TableCell>
                          <TableCell>{claim.claimedPattern.replace(/_/g, " ")}</TableCell>
                          <TableCell>{claim.status.replace(/_/g, " ")}</TableCell>
                          <TableCell>
                            {claim.serverValidationPassed ? "Passed" : "Failed"}
                            {claim.serverValidationMessage ? (
                              <Typography display="block" variant="caption" color="text.secondary">
                                {claim.serverValidationMessage}
                              </Typography>
                            ) : null}
                          </TableCell>
                          <TableCell>{new Date(claim.createdAt).toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                      {winRows.length === 0 ? <EmptyRow colSpan={6} /> : null}
                    </TableBody>
                  </>
                ) : null}
              </Table>
            </TableContainer>
          ) : null}

          {!activityQuery.isLoading && !activityQuery.error ? (
            <TablePagination
              component="div"
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
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

function EmptyRow({ colSpan }: { colSpan: number }) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan}>
        <Box py={2} textAlign="center">
          <Typography color="text.secondary">No records for this player.</Typography>
        </Box>
      </TableCell>
    </TableRow>
  );
}
