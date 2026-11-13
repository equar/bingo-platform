import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useLocation, useParams } from "react-router-dom";
import {
  assignSupportTicket,
  noteSupportTicket,
  replySupportTicket,
  supportTicketDetail,
  supportAssignees,
  updateSupportTicketStatus,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import {
  ListRefreshButton,
  ListRefreshError,
} from "../../components/ListRefreshButton";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus, translateAdminValue } from "../../i18n";

const statusOptions = [
  "OPEN",
  "IN_PROGRESS",
  "WAITING_FOR_CUSTOMER",
  "RESOLVED",
  "CLOSED",
] as const;

export function SupportTicketDetailPage() {
  const location = useLocation();
  const { t } = useTranslation();
  const { ticketId } = useParams();
  const id = Number(ticketId);
  const queryClient = useQueryClient();
  const ticketQuery = useQuery({
    queryKey: ["support-ticket", id],
    queryFn: () => supportTicketDetail(id),
    enabled: Number.isFinite(id),
  });
  const usersQuery = useQuery({
    queryKey: ["support-assignees"],
    queryFn: supportAssignees,
  });
  const [replyBody, setReplyBody] = useState("");
  const [noteBody, setNoteBody] = useState("");
  const [assignedToUserId, setAssignedToUserId] = useState("");
  const [status, setStatus] = useState<(typeof statusOptions)[number]>("OPEN");
  const [resolutionSummary, setResolutionSummary] = useState("");
  const [statusConfirmOpen, setStatusConfirmOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (ticketQuery.data) {
      setAssignedToUserId(
        ticketQuery.data.assignedToUserId
          ? String(ticketQuery.data.assignedToUserId)
          : "",
      );
      setStatus(ticketQuery.data.status as (typeof statusOptions)[number]);
      setResolutionSummary(ticketQuery.data.resolutionSummary ?? "");
    }
  }, [ticketQuery.data]);

  const ticket = ticketQuery.data;

  const assignMutation = useMutation({
    mutationFn: () => {
      if (!assignedToUserId) {
        throw new Error("Choose an assignee");
      }
      return assignSupportTicket(id, {
        assignedToUserId: Number(assignedToUserId),
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      await queryClient.invalidateQueries({ queryKey: ["support-tickets"] });
      setErrorMessage(null);
    },
    onError: (error) =>
      setErrorMessage(
        getUserFacingErrorMessage(error) ??
          "Something went wrong. Please try again.",
      ),
  });

  const replyMutation = useMutation({
    mutationFn: () => replySupportTicket(id, { body: replyBody }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      setReplyBody("");
      setErrorMessage(null);
    },
    onError: (error) =>
      setErrorMessage(
        getUserFacingErrorMessage(error) ??
          "Something went wrong. Please try again.",
      ),
  });

  const noteMutation = useMutation({
    mutationFn: () => noteSupportTicket(id, { body: noteBody }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      setNoteBody("");
      setErrorMessage(null);
    },
    onError: (error) =>
      setErrorMessage(
        getUserFacingErrorMessage(error) ??
          "Something went wrong. Please try again.",
      ),
  });

  const statusMutation = useMutation({
    mutationFn: () =>
      updateSupportTicketStatus(id, {
        status,
        resolutionSummary: ["RESOLVED", "CLOSED"].includes(status)
          ? resolutionSummary
          : null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      await queryClient.invalidateQueries({ queryKey: ["support-tickets"] });
      setStatusConfirmOpen(false);
      setErrorMessage(null);
    },
    onError: (error) =>
      setErrorMessage(
        getUserFacingErrorMessage(error) ??
          "Something went wrong. Please try again.",
      ),
  });

  const requiresResolutionSummary = useMemo(
    () => ["RESOLVED", "CLOSED"].includes(status),
    [status],
  );

  if (ticketQuery.isLoading || usersQuery.isLoading) {
    return (
      <StatusState
        title={t("Loading ticket")}
        loading
        message={t("Fetching ticket detail")}
      />
    );
  }

  if ((ticketQuery.error && !ticketQuery.data) || !ticket) {
    return (
      <StatusState
        title={t("Unable to load ticket")}
        message={t(
          getUserFacingErrorMessage(ticketQuery.error) ??
            "This item is no longer available. Refresh and try again.",
        )}
        actionLabel={t("Retry")}
        onAction={() => ticketQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box
        display="flex"
        justifyContent="space-between"
        alignItems="center"
        gap={2}
        flexWrap="wrap"
      >
        <div>
          <Typography variant="h4">{ticket.ticketNumber}</Typography>
          <Typography color="text.secondary">{ticket.subject}</Typography>
        </div>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <ListRefreshButton
            refreshing={ticketQuery.isFetching}
            onRefresh={() => ticketQuery.refetch()}
          />
          <Button
            component={RouterLink}
            to={`/support/tickets${location.search}`}
            variant="outlined"
          >
            {t("Back to queue")}
          </Button>
        </Stack>
      </Box>

      {ticketQuery.isRefetchError ? (
        <ListRefreshError
          retrying={ticketQuery.isFetching}
          error={ticketQuery.error}
          onRetry={() => ticketQuery.refetch()}
        />
      ) : null}

      {errorMessage ? <Alert severity="error">{t(errorMessage)}</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} lg={5}>
          <Card
            sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}
          >
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">{t("Ticket summary")}</Typography>
                <Typography>
                  {t("Requester:")} {ticket.requesterDisplayName}
                </Typography>
                <Typography>
                  {t("Category:")} {translateAdminValue(ticket.category, t)}
                </Typography>
                <Typography>
                  {t("Priority:")} {t(ticket.priority)}
                </Typography>
                <Typography>
                  {t("Status:")} {translateAdminStatus(ticket.status, t)}
                </Typography>
                <Typography>
                  {t("Linked record:")} {ticket.linkedEntityType ?? "n/a"}{" "}
                  {ticket.linkedEntityReference ??
                    ticket.linkedEntityId ??
                    "n/a"}
                </Typography>
                <Typography>
                  {t("Assigned to:")} {ticket.assignedToUserId ?? "Unassigned"}
                </Typography>
                <Typography>
                  {t("Created:")} {formatEthiopianDateTime(ticket.createdAt)}
                </Typography>
                <Typography>
                  {t("Updated:")} {formatEthiopianDateTime(ticket.updatedAt)}
                </Typography>
                <Divider />
                <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                  {ticket.description}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={7}>
          <Stack spacing={2}>
            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">{t("Assignment")}</Typography>
                  <TextField
                    select
                    label={t("Assignee")}
                    value={assignedToUserId}
                    onChange={(event) =>
                      setAssignedToUserId(event.target.value)
                    }
                    fullWidth
                  >
                    <MenuItem value="">{t("Unassigned")}</MenuItem>
                    {(usersQuery.data ?? []).map((user) => (
                      <MenuItem key={user.userId} value={String(user.userId)}>
                        {user.displayName}
                      </MenuItem>
                    ))}
                  </TextField>
                  <Button
                    variant="outlined"
                    onClick={() => assignMutation.mutate()}
                    disabled={!assignedToUserId || assignMutation.isPending}
                  >
                    {t("Save assignment")}
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">{t("Status update")}</Typography>
                  <TextField
                    select
                    label={t("New status")}
                    value={status}
                    onChange={(event) =>
                      setStatus(
                        event.target.value as (typeof statusOptions)[number],
                      )
                    }
                    fullWidth
                  >
                    {statusOptions.map((option) => (
                      <MenuItem key={option} value={option}>
                        {translateAdminStatus(option, t)}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    label={t("Resolution summary")}
                    value={resolutionSummary}
                    onChange={(event) =>
                      setResolutionSummary(event.target.value)
                    }
                    fullWidth
                    multiline
                    minRows={3}
                    required={requiresResolutionSummary}
                    helperText={
                      requiresResolutionSummary
                        ? t("Required before closing or resolving a ticket.")
                        : t("Optional unless closing or resolving.")
                    }
                  />
                  <Button
                    variant="contained"
                    onClick={() => setStatusConfirmOpen(true)}
                    disabled={
                      statusMutation.isPending ||
                      (requiresResolutionSummary && !resolutionSummary.trim())
                    }
                  >
                    {t("Review status change")}
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">{t("Public reply")}</Typography>
                  <TextField
                    label={t("Reply body")}
                    value={replyBody}
                    onChange={(event) => setReplyBody(event.target.value)}
                    fullWidth
                    multiline
                    minRows={4}
                  />
                  <Button
                    variant="outlined"
                    onClick={() => replyMutation.mutate()}
                    disabled={!replyBody.trim() || replyMutation.isPending}
                  >
                    {t("Send public reply")}
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">{t("Internal note")}</Typography>
                  <TextField
                    label={t("Internal note")}
                    value={noteBody}
                    onChange={(event) => setNoteBody(event.target.value)}
                    fullWidth
                    multiline
                    minRows={4}
                  />
                  <Button
                    variant="outlined"
                    onClick={() => noteMutation.mutate()}
                    disabled={!noteBody.trim() || noteMutation.isPending}
                  >
                    {t("Add internal note")}
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">{t("Public replies")}</Typography>
                {ticket.messages.length === 0 ? (
                  <Typography color="text.secondary">
                    {t("No public replies yet.")}
                  </Typography>
                ) : null}
                {ticket.messages.map((message) => (
                  <Card key={message.id} variant="outlined">
                    <CardContent>
                      <Stack spacing={0.75}>
                        <Chip
                          label={t("Public · {{id}}", {
                            id: message.authorUserId,
                          })}
                          size="small"
                          color="primary"
                        />
                        <Typography
                          variant="body2"
                          sx={{ whiteSpace: "pre-wrap" }}
                        >
                          {message.body}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatEthiopianDateTime(message.createdAt)}
                        </Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">{t("Internal notes")}</Typography>
                {ticket.internalNotes.length === 0 ? (
                  <Typography color="text.secondary">
                    {t("No internal notes yet.")}
                  </Typography>
                ) : null}
                {ticket.internalNotes.map((note) => (
                  <Card key={note.id} variant="outlined">
                    <CardContent>
                      <Stack spacing={0.75}>
                        <Chip
                          label={t("Internal · {{id}}", {
                            id: note.authorUserId,
                          })}
                          size="small"
                          color="warning"
                        />
                        <Typography
                          variant="body2"
                          sx={{ whiteSpace: "pre-wrap" }}
                        >
                          {note.body}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatEthiopianDateTime(note.createdAt)}
                        </Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={statusConfirmOpen}
        title={t("Update ticket status")}
        description={
          requiresResolutionSummary
            ? t(
                "This status change requires a resolution summary and will not be submitted until the backend accepts it.",
              )
            : t("Submit the status change to the backend.")
        }
        confirmLabel={t("Confirm")}
        cancelLabel={t("Back")}
        confirming={statusMutation.isPending}
        onCancel={() => setStatusConfirmOpen(false)}
        onConfirm={() => statusMutation.mutate()}
      />
    </Stack>
  );
}
