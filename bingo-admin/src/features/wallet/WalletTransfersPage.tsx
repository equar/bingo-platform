import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { walletTransfers } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

export function WalletTransfersPage() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [senderPhone, setSenderPhone] = useState("");
  const [recipientPhone, setRecipientPhone] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const transfersQuery = useQuery({
    queryKey: ["wallet-transfers", query, status, senderPhone, recipientPhone],
    queryFn: () =>
      walletTransfers(
        query.trim() || undefined,
        status.trim() || undefined,
        senderPhone.trim() || undefined,
        recipientPhone.trim() || undefined,
        0,
        50,
      ),
  });

  const selectedTransfer = useMemo(
    () => transfersQuery.data?.content.find((transfer) => transfer.id === selectedId) ?? null,
    [selectedId, transfersQuery.data],
  );

  if (transfersQuery.isLoading) {
    return <StatusState title="Loading transfers" loading message="Fetching player transfer history" />;
  }

  if (transfersQuery.error) {
    return (
      <StatusState
        title="Unable to load transfers"
        message={(transfersQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => transfersQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" gutterBottom>
          Transfer history
        </Typography>
        <Typography color="text.secondary">
          Search player-to-player wallet transfers by sender, recipient, or note.
        </Typography>
      </Box>

      <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid item xs={12} md={3}>
              <TextField label="Search" value={query} onChange={(event) => setQuery(event.target.value)} fullWidth />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                label="Status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
                fullWidth
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="COMPLETED">Completed</MenuItem>
                <MenuItem value="FAILED">Failed</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                label="Sender phone"
                value={senderPhone}
                onChange={(event) => setSenderPhone(event.target.value)}
                fullWidth
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                label="Recipient phone"
                value={recipientPhone}
                onChange={(event) => setRecipientPhone(event.target.value)}
                fullWidth
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={7}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <List dense>
                {transfersQuery.data?.content.map((transfer) => (
                  <ListItemButton
                    key={transfer.id}
                    selected={selectedId === transfer.id}
                    onClick={() => setSelectedId(transfer.id)}
                    sx={{ borderRadius: 2, mb: 0.5 }}
                  >
                    <ListItemText
                      primary={`${transfer.senderPhoneNumber} → ${transfer.recipientPhoneNumber}`}
                      secondary={`${transfer.amount} ${transfer.currency} · ${new Date(transfer.createdAt).toLocaleString()} · ${transfer.note ?? "No note"}`}
                    />
                    <Chip label={transfer.status} size="small" />
                  </ListItemButton>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={5}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", minHeight: 260 }}>
            <CardContent>
              {selectedTransfer ? (
                <Stack spacing={1.5}>
                  <Typography variant="h6">Transfer detail</Typography>
                  <Typography>Operation: {selectedTransfer.operationId}</Typography>
                  <Typography>
                    Sender: {selectedTransfer.senderPhoneNumber} ({selectedTransfer.senderId})
                  </Typography>
                  <Typography>
                    Recipient: {selectedTransfer.recipientPhoneNumber} ({selectedTransfer.recipientId})
                  </Typography>
                  <Typography>
                    Amount: {selectedTransfer.amount} {selectedTransfer.currency}
                  </Typography>
                  <Typography>Status: {selectedTransfer.status}</Typography>
                  <Typography>Created: {new Date(selectedTransfer.createdAt).toLocaleString()}</Typography>
                  <Typography>
                    Completed:{" "}
                    {selectedTransfer.completedAt ? new Date(selectedTransfer.completedAt).toLocaleString() : "Pending"}
                  </Typography>
                  <Typography>
                    Sender before/after: {selectedTransfer.senderBalanceBefore} → {selectedTransfer.senderBalanceAfter}
                  </Typography>
                  <Typography>
                    Recipient before/after: {selectedTransfer.recipientBalanceBefore} →{" "}
                    {selectedTransfer.recipientBalanceAfter}
                  </Typography>
                  <Typography>Note: {selectedTransfer.note ?? "None"}</Typography>
                  <Typography color="text.secondary">
                    {selectedTransfer.failureReason ?? "No failure reason"}
                  </Typography>
                </Stack>
              ) : (
                <Typography color="text.secondary">Select a transfer to inspect the details.</Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
