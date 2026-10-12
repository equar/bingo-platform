import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
  List,
  ListItem,
  ListItemText,
  Chip,
} from "@mui/material";
import { BingoPatternConfig, BingoRoomResponse, createRoom, listRooms, updateRoom } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";

interface RoomDraft {
  id?: number;
  name: string;
  description: string;
  ticketPrice: string;
  currency: string;
  timezone: string;
  scheduleRule: string;
  sessionCapacity: string;
  maxTicketsPerPlayer: string;
  ticketSalesCloseOnSessionStart: boolean;
  allowMultipleWinnersPerPrize: boolean;
  active: boolean;
  configuredPatterns: string;
  configuredPrizes: string;
}

const emptyDraft: RoomDraft = {
  name: "",
  description: "",
  ticketPrice: "0.00",
  currency: "ETB",
  timezone: "Africa/Addis_Ababa",
  scheduleRule: "WEEKLY",
  sessionCapacity: "100",
  maxTicketsPerPlayer: "5",
  ticketSalesCloseOnSessionStart: true,
  allowMultipleWinnersPerPrize: true,
  active: true,
  configuredPatterns: "LINE, TWO_LINES, FULL_HOUSE",
  configuredPrizes: JSON.stringify(
    [{ prizeType: "LINE", amount: 100, settlementPath: "PLAYER_WALLET", maxWinners: 1 }],
    null,
    2,
  ),
};

function roomToDraft(room: BingoRoomResponse): RoomDraft {
  return {
    id: room.id,
    name: room.name,
    description: room.description ?? "",
    ticketPrice: room.ticketPrice,
    currency: room.currency,
    timezone: room.timezone,
    scheduleRule: room.scheduleRule,
    sessionCapacity: String(room.sessionCapacity),
    maxTicketsPerPlayer: String(room.maxTicketsPerPlayer),
    ticketSalesCloseOnSessionStart: room.ticketSalesCloseOnSessionStart,
    allowMultipleWinnersPerPrize: room.allowMultipleWinnersPerPrize,
    active: room.active,
    configuredPatterns: room.configuredPatterns.join(", "),
    configuredPrizes: JSON.stringify(room.configuredPrizes, null, 2),
  };
}

function parsePrizes(json: string): BingoPatternConfig[] {
  const value = JSON.parse(json) as Array<{
    prizeType: string;
    amount: number;
    settlementPath: string;
    maxWinners?: number | null;
  }>;
  return value.map((item) => ({
    prizeType: item.prizeType,
    amount: Number(item.amount),
    settlementPath: item.settlementPath,
    maxWinners: item.maxWinners ?? null,
  }));
}

export function RoomsPage() {
  const queryClient = useQueryClient();
  const roomsQuery = useQuery({ queryKey: ["rooms"], queryFn: listRooms });
  const [draft, setDraft] = useState<RoomDraft | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingSubmit, setPendingSubmit] = useState(false);
  const [pendingError, setPendingError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (body: RoomDraft) =>
      createRoom({
        name: body.name,
        description: body.description || null,
        ticketPrice: body.ticketPrice,
        currency: body.currency,
        timezone: body.timezone,
        scheduleRule: body.scheduleRule,
        sessionCapacity: Number(body.sessionCapacity),
        maxTicketsPerPlayer: Number(body.maxTicketsPerPlayer),
        ticketSalesCloseOnSessionStart: body.ticketSalesCloseOnSessionStart,
        allowMultipleWinnersPerPrize: body.allowMultipleWinnersPerPrize,
        active: body.active,
        configuredPatterns: body.configuredPatterns
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
        configuredPrizes: parsePrizes(body.configuredPrizes),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["rooms"] });
      setDraft(null);
      setConfirmOpen(false);
      setPendingError(null);
    },
    onError: (error) => setPendingError((error as Error).message),
    onSettled: () => setPendingSubmit(false),
  });

  const updateMutation = useMutation({
    mutationFn: (body: RoomDraft) =>
      updateRoom(body.id!, {
        name: body.name,
        description: body.description || null,
        ticketPrice: body.ticketPrice,
        currency: body.currency,
        timezone: body.timezone,
        scheduleRule: body.scheduleRule,
        sessionCapacity: Number(body.sessionCapacity),
        maxTicketsPerPlayer: Number(body.maxTicketsPerPlayer),
        ticketSalesCloseOnSessionStart: body.ticketSalesCloseOnSessionStart,
        allowMultipleWinnersPerPrize: body.allowMultipleWinnersPerPrize,
        active: body.active,
        configuredPatterns: body.configuredPatterns
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
        configuredPrizes: parsePrizes(body.configuredPrizes),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["rooms"] });
      setDraft(null);
      setConfirmOpen(false);
      setPendingError(null);
    },
    onError: (error) => setPendingError((error as Error).message),
    onSettled: () => setPendingSubmit(false),
  });

  const onSubmit = () => {
    if (!draft) {
      return;
    }
    setPendingSubmit(true);
    setPendingError(null);
    if (draft.id) {
      updateMutation.mutate(draft);
      return;
    }
    createMutation.mutate(draft);
  };

  const rooms = roomsQuery.data ?? [];
  const title = useMemo(() => (draft?.id ? "Edit room" : "Create room"), [draft?.id]);

  if (roomsQuery.isLoading) {
    return <StatusState title="Loading rooms" loading message="Fetching room catalog" />;
  }

  if (roomsQuery.error) {
    return (
      <StatusState
        title="Unable to load rooms"
        message={(roomsQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => roomsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">Rooms</Typography>
          <Typography color="text.secondary">Create and edit bingo room configuration and activation.</Typography>
        </div>
        <Button variant="contained" onClick={() => setDraft({ ...emptyDraft })}>
          New room
        </Button>
      </Box>

      <Grid container spacing={2}>
        {rooms.map((room) => (
          <Grid item xs={12} md={6} key={room.id}>
            <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardContent>
                <Stack spacing={1.5}>
                  <Box display="flex" justifyContent="space-between" gap={2} alignItems="start">
                    <div>
                      <Typography variant="h6">{room.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {room.description ?? "No description"}
                      </Typography>
                    </div>
                    <Chip
                      label={room.active ? "Active" : "Inactive"}
                      color={room.active ? "success" : "default"}
                      size="small"
                    />
                  </Box>
                  <Typography variant="body2">
                    Price: {room.ticketPrice} {room.currency}
                  </Typography>
                  <Typography variant="body2">
                    Capacity: {room.sessionCapacity} · Max tickets: {room.maxTicketsPerPlayer}
                  </Typography>
                  <Typography variant="body2">Schedule: {room.scheduleRule}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {room.configuredPatterns.map((pattern) => (
                      <Chip key={pattern} label={pattern} size="small" />
                    ))}
                  </Stack>
                  <Divider />
                  <Button variant="outlined" onClick={() => setDraft(roomToDraft(room))}>
                    Edit
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {rooms.length === 0 ? <Alert severity="info">No rooms have been configured yet.</Alert> : null}

      <Dialog open={Boolean(draft)} onClose={() => setDraft(null)} fullWidth maxWidth="md">
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {pendingError ? <Alert severity="error">{pendingError}</Alert> : null}
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Room name"
                  value={draft?.name ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, name: e.target.value })}
                  fullWidth
                  required
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Currency"
                  value={draft?.currency ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, currency: e.target.value.toUpperCase() })}
                  fullWidth
                  required
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Description"
                  value={draft?.description ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, description: e.target.value })}
                  fullWidth
                  multiline
                  minRows={2}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Ticket price"
                  value={draft?.ticketPrice ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, ticketPrice: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Timezone"
                  value={draft?.timezone ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, timezone: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Schedule rule"
                  value={draft?.scheduleRule ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, scheduleRule: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Session capacity"
                  value={draft?.sessionCapacity ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, sessionCapacity: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Max tickets per player"
                  value={draft?.maxTicketsPerPlayer ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, maxTicketsPerPlayer: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Configured patterns"
                  value={draft?.configuredPatterns ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, configuredPatterns: e.target.value })}
                  fullWidth
                  helperText="Comma-separated BingoPatternType values"
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Configured prizes JSON"
                  value={draft?.configuredPrizes ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, configuredPrizes: e.target.value })}
                  fullWidth
                  multiline
                  minRows={8}
                  helperText="Array of { prizeType, amount, settlementPath, maxWinners }"
                />
              </Grid>
            </Grid>
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft?.ticketSalesCloseOnSessionStart ?? false}
                  onChange={(e) => draft && setDraft({ ...draft, ticketSalesCloseOnSessionStart: e.target.checked })}
                />
              }
              label="Close ticket sales on session start"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft?.allowMultipleWinnersPerPrize ?? false}
                  onChange={(e) => draft && setDraft({ ...draft, allowMultipleWinnersPerPrize: e.target.checked })}
                />
              }
              label="Allow multiple winners per prize"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft?.active ?? false}
                  onChange={(e) => draft && setDraft({ ...draft, active: e.target.checked })}
                />
              }
              label="Active"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraft(null)} disabled={pendingSubmit}>
            Cancel
          </Button>
          <Button variant="contained" onClick={() => setConfirmOpen(true)} disabled={pendingSubmit}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        title={title}
        description="This will save the room configuration on the backend."
        confirmLabel="Confirm"
        cancelLabel="Back"
        confirming={pendingSubmit}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={onSubmit}
      />
    </Stack>
  );
}
