import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  Stack,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import {
  adminSessionDetail,
  adminTicketDetail,
  adminWinClaimDetail,
  playerDetail,
  type AdminPlayerDetailResponse,
  type GameSessionResponse,
  type TicketResponse,
  type WinClaimResponse,
} from "../../api/admin";
import { StatusState } from "../../components/StatusState";

export type EntitlementReference = {
  kind: "player" | "claim" | "session" | "ticket";
  id: number;
};

type ReferenceRecord = AdminPlayerDetailResponse | WinClaimResponse | GameSessionResponse | TicketResponse;
interface DetailField {
  label: string;
  value: string;
}

interface EntitlementReferenceDialogProps {
  reference: EntitlementReference | null;
  onClose: () => void;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : "Not available";
}

function detailFields(reference: EntitlementReference, record: ReferenceRecord): DetailField[] {
  if (reference.kind === "player") {
    const player = record as AdminPlayerDetailResponse;
    return [
      { label: "Player ID", value: String(player.id) },
      { label: "Name", value: player.displayName },
      { label: "Email", value: player.email ?? "Not provided" },
      { label: "Phone", value: player.normalizedPhone },
      { label: "Status", value: player.status },
      { label: "Roles", value: player.roles.join(", ") || "None" },
      { label: "Wallet currency", value: player.walletCurrency ?? "No wallet" },
      { label: "Available balance", value: player.walletAvailableBalance ?? "Not available" },
      { label: "Promotional balance", value: player.walletPromotionalBalance ?? "Not available" },
      { label: "Wallet transactions", value: String(player.walletTransactionCount) },
      { label: "Deposits", value: String(player.depositClaimCount) },
      { label: "Support tickets", value: String(player.supportTicketCount) },
      { label: "Game tickets / wins", value: `${player.ticketCount} / ${player.winClaimCount}` },
      { label: "Created", value: formatDate(player.createdAt) },
      { label: "Last updated", value: formatDate(player.updatedAt) },
    ];
  }

  if (reference.kind === "claim") {
    const claim = record as WinClaimResponse;
    return [
      { label: "Claim ID", value: String(claim.id) },
      { label: "Player ID", value: String(claim.claimantId) },
      { label: "Ticket / session", value: `${claim.ticketId} / ${claim.sessionId}` },
      { label: "Pattern", value: claim.claimedPattern.replace(/_/g, " ") },
      { label: "Status", value: claim.status.replace(/_/g, " ") },
      { label: "Server validation", value: claim.serverValidationPassed ? "Passed" : "Failed" },
      { label: "Validation code", value: claim.serverValidationCode ?? "Not available" },
      { label: "Validation message", value: claim.serverValidationMessage ?? "Not available" },
      { label: "Draw sequence", value: String(claim.drawSequenceSnapshot) },
      { label: "Called numbers", value: claim.calledNumbersSnapshot.join(", ") || "None" },
      { label: "Review note", value: claim.reviewNote ?? "No review note" },
      { label: "Reviewed at", value: formatDate(claim.reviewedAt) },
      { label: "Entitlement ID", value: claim.entitlementId == null ? "Not available" : String(claim.entitlementId) },
      { label: "Operation ID", value: claim.operationId },
      { label: "Created", value: formatDate(claim.createdAt) },
      { label: "Last updated", value: formatDate(claim.updatedAt) },
    ];
  }

  if (reference.kind === "session") {
    const session = record as GameSessionResponse;
    return [
      { label: "Session ID", value: String(session.id) },
      { label: "Name", value: session.sessionName },
      { label: "Room", value: `${session.roomName} (#${session.roomId})` },
      { label: "State", value: session.state },
      { label: "Scheduled start", value: formatDate(session.scheduledStartAt) },
      { label: "Started", value: formatDate(session.startedAt) },
      { label: "Ended", value: formatDate(session.endedAt) },
      { label: "Ticket price", value: `${session.ticketPrice} ${session.currency}` },
      { label: "Capacity / max per player", value: `${session.sessionCapacity} / ${session.maxTicketsPerPlayer}` },
      { label: "Tickets / draws", value: `${session.ticketCount} / ${session.drawCount}` },
      { label: "Patterns", value: session.configuredPatterns.join(", ") || "None" },
      { label: "Prizes", value: JSON.stringify(session.configuredPrizes) },
      { label: "Created", value: formatDate(session.createdAt) },
      { label: "Last updated", value: formatDate(session.updatedAt) },
    ];
  }

  const ticket = record as TicketResponse;
  return [
    { label: "Ticket ID", value: String(ticket.id) },
    { label: "Public code", value: ticket.publicTicketCode },
    { label: "Player ID", value: String(ticket.ownerId) },
    { label: "Session ID", value: String(ticket.sessionId) },
    { label: "Operation ID", value: ticket.operationId },
    { label: "Purchased", value: formatDate(ticket.purchasedAt) },
    { label: "Card grid", value: ticket.grid.map((row) => row.join("  ")).join("\n") },
  ];
}

export function EntitlementReferenceDialog({ reference, onClose }: EntitlementReferenceDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down("sm"));
  const detailQuery = useQuery<ReferenceRecord>({
    queryKey: ["entitlement-reference", reference?.kind, reference?.id],
    queryFn: () => {
      if (!reference) {
        throw new Error("A reference is required");
      }
      switch (reference.kind) {
        case "player":
          return playerDetail(reference.id);
        case "claim":
          return adminWinClaimDetail(reference.id);
        case "session":
          return adminSessionDetail(reference.id);
        case "ticket":
          return adminTicketDetail(reference.id);
      }
    },
    enabled: reference != null,
  });
  const title = reference
    ? `${reference.kind === "claim" ? "Claim" : reference.kind[0].toUpperCase() + reference.kind.slice(1)} #${reference.id}`
    : "Record details";
  const fields = reference && detailQuery.data ? detailFields(reference, detailQuery.data) : [];

  return (
    <Dialog
      open={reference != null}
      onClose={onClose}
      fullWidth
      fullScreen={fullScreen}
      maxWidth="md"
      aria-labelledby="entitlement-reference-title"
    >
      <DialogTitle id="entitlement-reference-title">{title}</DialogTitle>
      <DialogContent dividers>
        {detailQuery.isLoading ? <StatusState title="Loading record" loading message="Fetching details" /> : null}
        {detailQuery.error ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={() => detailQuery.refetch()}>
                Retry
              </Button>
            }
          >
            {(detailQuery.error as Error).message || "Unable to load record details."}
          </Alert>
        ) : null}
        {detailQuery.data ? (
          <Stack divider={<Divider flexItem />}>
            {fields.map((field) => (
              <Grid container spacing={2} key={field.label} sx={{ py: 1.25 }}>
                <Grid item xs={12} sm={4}>
                  <Typography variant="body2" color="text.secondary">
                    {field.label}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={8}>
                  <Typography variant="body2" sx={{ overflowWrap: "anywhere", whiteSpace: "pre-wrap" }}>
                    {field.value}
                  </Typography>
                </Grid>
              </Grid>
            ))}
          </Stack>
        ) : null}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
