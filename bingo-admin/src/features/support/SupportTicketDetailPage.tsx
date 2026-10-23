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
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  assignSupportTicket,
  noteSupportTicket,
  replySupportTicket,
  supportTicketDetail,
  supportAssignees,
  updateSupportTicketStatus,
} from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";

const statusOptions = ["OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"] as const;

export function SupportTicketDetailPage() {
  const { ticketId } = useParams();
  const id = Number(ticketId);
  const queryClient = useQueryClient();
  const ticketQuery = useQuery({
    queryKey: ["support-ticket", id],
    queryFn: () => supportTicketDetail(id),
    enabled: Number.isFinite(id),
  });
  const usersQuery = useQuery({ queryKey: ["support-assignees"], queryFn: supportAssignees });
  const [replyBody, setReplyBody] = useState("");
  const [noteBody, setNoteBody] = useState("");
  const [assignedToUserId, setAssignedToUserId] = useState("");
  const [status, setStatus] = useState<(typeof statusOptions)[number]>("OPEN");
  const [resolutionSummary, setResolutionSummary] = useState("");
  const [statusConfirmOpen, setStatusConfirmOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (ticketQuery.data) {
      setAssignedToUserId(ticketQuery.data.assignedToUserId ? String(ticketQuery.data.assignedToUserId) : "");
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
      return assignSupportTicket(id, { assignedToUserId: Number(assignedToUserId) });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      await queryClient.invalidateQueries({ queryKey: ["support-tickets"] });
      setErrorMessage(null);
    },
    onError: (error) => setErrorMessage((error as Error).message),
  });

  const replyMutation = useMutation({
    mutationFn: () => replySupportTicket(id, { body: replyBody }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      setReplyBody("");
      setErrorMessage(null);
    },
    onError: (error) => setErrorMessage((error as Error).message),
  });

  const noteMutation = useMutation({
    mutationFn: () => noteSupportTicket(id, { body: noteBody }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      setNoteBody("");
      setErrorMessage(null);
    },
    onError: (error) => setErrorMessage((error as Error).message),
  });

  const statusMutation = useMutation({
    mutationFn: () =>
      updateSupportTicketStatus(id, {
        status,
        resolutionSummary: ["RESOLVED", "CLOSED"].includes(status) ? resolutionSummary : null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["support-ticket", id] });
      await queryClient.invalidateQueries({ queryKey: ["support-tickets"] });
      setStatusConfirmOpen(false);
      setErrorMessage(null);
    },
    onError: (error) => setErrorMessage((error as Error).message),
  });

  const requiresResolutionSummary = useMemo(() => ["RESOLVED", "CLOSED"].includes(status), [status]);

  if (ticketQuery.isLoading || usersQuery.isLoading) {
    return <StatusState title="Loading ticket" loading message="Fetching ticket detail" />;
  }

  if (ticketQuery.error || !ticket) {
    return (
      <StatusState
        title="Unable to load ticket"
        message={(ticketQuery.error as Error | undefined)?.message ?? "Ticket not found"}
        actionLabel="Retry"
        onAction={() => ticketQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">{ticket.ticketNumber}</Typography>
          <Typography color="text.secondary">{ticket.subject}</Typography>
        </div>
        <Button component={RouterLink} to="/support/tickets" variant="outlined">
          Back to queue
        </Button>
      </Box>

      {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} lg={5}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Ticket summary</Typography>
                <Typography>Requester: {ticket.requesterDisplayName}</Typography>
                <Typography>Category: {ticket.category}</Typography>
                <Typography>Priority: {ticket.priority}</Typography>
                <Typography>Status: {ticket.status}</Typography>
                <Typography>
                  Linked record: {ticket.linkedEntityType ?? "n/a"}{" "}
                  {ticket.linkedEntityReference ?? ticket.linkedEntityId ?? "n/a"}
                </Typography>
                <Typography>Assigned to: {ticket.assignedToUserId ?? "Unassigned"}</Typography>
                <Typography>Created: {new Date(ticket.createdAt).toLocaleString()}</Typography>
                <Typography>Updated: {new Date(ticket.updatedAt).toLocaleString()}</Typography>
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
                  <Typography variant="h6">Assignment</Typography>
                  <TextField
                    select
                    label="Assignee"
                    value={assignedToUserId}
                    onChange={(event) => setAssignedToUserId(event.target.value)}
                    fullWidth
                  >
                    <MenuItem value="">Unassigned</MenuItem>
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
                    Save assignment
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">Status update</Typography>
                  <TextField
                    select
                    label="New status"
                    value={status}
                    onChange={(event) => setStatus(event.target.value as (typeof statusOptions)[number])}
                    fullWidth
                  >
                    {statusOptions.map((option) => (
                      <MenuItem key={option} value={option}>
                        {option}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    label="Resolution summary"
                    value={resolutionSummary}
                    onChange={(event) => setResolutionSummary(event.target.value)}
                    fullWidth
                    multiline
                    minRows={3}
                    required={requiresResolutionSummary}
                    helperText={
                      requiresResolutionSummary
                        ? "Required before closing or resolving a ticket."
                        : "Optional unless closing or resolving."
                    }
                  />
                  <Button
                    variant="contained"
                    onClick={() => setStatusConfirmOpen(true)}
                    disabled={statusMutation.isPending || (requiresResolutionSummary && !resolutionSummary.trim())}
                  >
                    Review status change
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">Public reply</Typography>
                  <TextField
                    label="Reply body"
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
                    Send public reply
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ border: "1px solid", borderColor: "divider" }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">Internal note</Typography>
                  <TextField
                    label="Internal note"
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
                    Add internal note
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
                <Typography variant="h6">Public replies</Typography>
                {ticket.messages.length === 0 ? (
                  <Typography color="text.secondary">No public replies yet.</Typography>
                ) : null}
                {ticket.messages.map((message) => (
                  <Card key={message.id} variant="outlined">
                    <CardContent>
                      <Stack spacing={0.75}>
                        <Chip label={`Public · ${message.authorUserId}`} size="small" color="primary" />
                        <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                          {message.body}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {new Date(message.createdAt).toLocaleString()}
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
                <Typography variant="h6">Internal notes</Typography>
                {ticket.internalNotes.length === 0 ? (
                  <Typography color="text.secondary">No internal notes yet.</Typography>
                ) : null}
                {ticket.internalNotes.map((note) => (
                  <Card key={note.id} variant="outlined">
                    <CardContent>
                      <Stack spacing={0.75}>
                        <Chip label={`Internal · ${note.authorUserId}`} size="small" color="warning" />
                        <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                          {note.body}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {new Date(note.createdAt).toLocaleString()}
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
        title="Update ticket status"
        description={
          requiresResolutionSummary
            ? "This status change requires a resolution summary and will not be submitted until the backend accepts it."
            : "Submit the status change to the backend."
        }
        confirmLabel="Confirm"
        cancelLabel="Back"
        confirming={statusMutation.isPending}
        onCancel={() => setStatusConfirmOpen(false)}
        onConfirm={() => statusMutation.mutate()}
      />
    </Stack>
  );
}
