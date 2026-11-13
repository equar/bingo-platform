import { useTranslation } from "react-i18next";
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
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import {
  translateAdminRole,
  translateAdminStatus,
  translateAdminValue,
} from "../../i18n";
import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { WinningTicketCard } from "../../components/WinningTicketCard";

export type EntitlementReference = {
  kind: "player" | "claim" | "session" | "ticket";
  id: number;
  winningClaimId?: number;
};

type ReferenceRecord =
  | AdminPlayerDetailResponse
  | WinClaimResponse
  | GameSessionResponse
  | TicketResponse;
interface DetailField {
  label: string;
  value: string;
}

interface EntitlementReferenceDialogProps {
  reference: EntitlementReference | null;
  onClose: () => void;
}

function formatDate(value: string | null, t: (key: string) => string) {
  return value
    ? formatEthiopianDateTime(value, t("Not available"))
    : t("Not available");
}

function detailFields(
  reference: EntitlementReference,
  record: ReferenceRecord,
  t: (key: string) => string,
): DetailField[] {
  if (reference.kind === "player") {
    const player = record as AdminPlayerDetailResponse;
    return [
      { label: t("Player ID"), value: String(player.id) },
      { label: t("Name"), value: player.displayName },
      { label: t("Email"), value: player.email ?? t("Not provided") },
      { label: t("Phone"), value: player.normalizedPhone },
      { label: t("Status"), value: translateAdminStatus(player.status, t) },
      {
        label: t("Roles"),
        value:
          player.roles.map((role) => translateAdminRole(role, t)).join(", ") ||
          t("None"),
      },
      {
        label: t("Wallet currency"),
        value: player.walletCurrency ?? t("No wallet"),
      },
      {
        label: t("Available balance"),
        value: player.walletAvailableBalance ?? t("Not available"),
      },
      {
        label: t("Promotional balance"),
        value: player.walletPromotionalBalance ?? t("Not available"),
      },
      {
        label: t("Wallet transactions"),
        value: String(player.walletTransactionCount),
      },
      { label: t("Deposits"), value: String(player.depositClaimCount) },
      { label: t("Support tickets"), value: String(player.supportTicketCount) },
      {
        label: t("Game tickets / wins"),
        value: `${player.ticketCount} / ${player.winClaimCount}`,
      },
      { label: t("Created"), value: formatDate(player.createdAt, t) },
      { label: t("Last updated"), value: formatDate(player.updatedAt, t) },
    ];
  }

  if (reference.kind === "claim") {
    const claim = record as WinClaimResponse;
    return [
      { label: t("Claim number"), value: claim.operationId },
      {
        label: t("Player"),
        value: `${claim.claimantDisplayName ?? t("Not available")} (#${claim.claimantId})`,
      },
      {
        label: t("Phone"),
        value: claim.claimantPhoneNumber ?? t("Not available"),
      },
      {
        label: t("Ticket"),
        value: `${claim.publicTicketCode ?? t("Not available")} (#${claim.ticketId})`,
      },
      { label: t("Session ID"), value: String(claim.sessionId) },
      {
        label: t("Pattern"),
        value: translateAdminValue(claim.claimedPattern, t),
      },
      { label: t("Status"), value: translateAdminStatus(claim.status, t) },
      {
        label: t("Server validation"),
        value: claim.serverValidationPassed ? t("Passed") : t("Failed"),
      },
      {
        label: t("Validation code"),
        value: claim.serverValidationCode ?? t("Not available"),
      },
      {
        label: t("Validation message"),
        value: claim.serverValidationMessage ?? t("Not available"),
      },
      { label: t("Draw sequence"), value: String(claim.drawSequenceSnapshot) },
      {
        label: t("Called numbers"),
        value: claim.calledNumbersSnapshot.join(", ") || t("None"),
      },
      {
        label: t("Claim evidence"),
        value: claim.evidenceJson ?? t("Not available"),
      },
      {
        label: t("Review note"),
        value: claim.reviewNote ?? t("No review note"),
      },
      {
        label: t("Reviewed by"),
        value:
          claim.reviewedBy == null
            ? t("Not available")
            : String(claim.reviewedBy),
      },
      { label: t("Reviewed at"), value: formatDate(claim.reviewedAt, t) },
      {
        label: t("Entitlement ID"),
        value:
          claim.entitlementId == null
            ? t("Not available")
            : String(claim.entitlementId),
      },
      { label: t("Created"), value: formatDate(claim.createdAt, t) },
      { label: t("Last updated"), value: formatDate(claim.updatedAt, t) },
    ];
  }

  if (reference.kind === "session") {
    const session = record as GameSessionResponse;
    return [
      { label: t("Session ID"), value: String(session.id) },
      { label: t("Name"), value: session.sessionName },
      { label: t("Room"), value: `${session.roomName} (#${session.roomId})` },
      { label: t("State"), value: translateAdminStatus(session.state, t) },
      {
        label: t("Scheduled start"),
        value: formatDate(session.scheduledStartAt, t),
      },
      { label: t("Started"), value: formatDate(session.startedAt, t) },
      { label: t("Ended"), value: formatDate(session.endedAt, t) },
      {
        label: t("Ticket price"),
        value: `${session.ticketPrice} ${session.currency}`,
      },
      {
        label: t("Capacity / max per player"),
        value: `${session.sessionCapacity} / ${session.maxTicketsPerPlayer}`,
      },
      {
        label: t("Tickets / draws"),
        value: `${session.ticketCount} / ${session.drawCount}`,
      },
      {
        label: t("Patterns"),
        value:
          session.configuredPatterns
            .map((pattern) => translateAdminValue(pattern, t))
            .join(", ") || t("None"),
      },
      { label: t("Prizes"), value: JSON.stringify(session.configuredPrizes) },
      { label: t("Created"), value: formatDate(session.createdAt, t) },
      { label: t("Last updated"), value: formatDate(session.updatedAt, t) },
    ];
  }

  const ticket = record as TicketResponse;
  return [
    { label: t("Ticket ID"), value: String(ticket.id) },
    { label: t("Public code"), value: ticket.publicTicketCode },
    { label: t("Player ID"), value: String(ticket.ownerId) },
    { label: t("Session ID"), value: String(ticket.sessionId) },
    { label: t("Operation ID"), value: ticket.operationId },
    { label: t("Purchased"), value: formatDate(ticket.purchasedAt, t) },
  ];
}

export function EntitlementReferenceDialog({
  reference,
  onClose,
}: EntitlementReferenceDialogProps) {
  const { t } = useTranslation();
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
  const winningClaimQuery = useQuery({
    queryKey: ["winning-ticket-claim", reference?.winningClaimId],
    queryFn: () => adminWinClaimDetail(reference!.winningClaimId!),
    enabled: reference?.kind === "ticket" && reference.winningClaimId != null,
  });
  const referenceType = reference
    ? t(
        reference.kind === "claim"
          ? "Claim"
          : reference.kind === "player"
            ? "Player"
            : reference.kind === "session"
              ? "Session"
              : "Ticket",
      )
    : "";
  const title = !reference
    ? t("Record details")
    : reference.kind === "claim"
      ? detailQuery.data
        ? `${t("Claim number")} ${(detailQuery.data as WinClaimResponse).operationId}`
        : t("Claim details")
      : reference.kind === "ticket" && detailQuery.data
        ? `${t("Ticket")} ${(detailQuery.data as TicketResponse).publicTicketCode}`
      : t("{{kind}} #{{id}}", { kind: referenceType, id: reference.id });
  const fields =
    reference && detailQuery.data
      ? detailFields(reference, detailQuery.data, t)
      : [];

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
        {detailQuery.isLoading ? (
          <StatusState
            title={t("Loading record")}
            loading
            message={t("Fetching details")}
          />
        ) : null}
        {detailQuery.error ? (
          <Alert
            severity="error"
            action={
              <Button
                color="inherit"
                size="small"
                onClick={() => detailQuery.refetch()}
              >
                {t("Retry")}
              </Button>
            }
          >
            {t(
              getUserFacingErrorMessage(detailQuery.error) ??
                "Unable to load record details.",
            )}
          </Alert>
        ) : null}
        {detailQuery.data ? (
          <>
            <Stack divider={<Divider flexItem />}>
              {fields.map((field) => (
                <Grid container spacing={2} key={field.label} sx={{ py: 1.25 }}>
                  <Grid item xs={12} sm={4}>
                    <Typography variant="body2" color="text.secondary">
                      {field.label}
                    </Typography>
                  </Grid>
                  <Grid item xs={12} sm={8}>
                    <Typography
                      variant="body2"
                      sx={{ overflowWrap: "anywhere", whiteSpace: "pre-wrap" }}
                    >
                      {field.value}
                    </Typography>
                  </Grid>
                </Grid>
              ))}
            </Stack>
            {reference?.kind === "ticket" && winningClaimQuery.data ? (
              <WinningTicketCard
                ticket={detailQuery.data as TicketResponse}
                claim={winningClaimQuery.data}
                t={t}
              />
            ) : null}
          </>
        ) : null}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("Close")}</Button>
      </DialogActions>
    </Dialog>
  );
}
