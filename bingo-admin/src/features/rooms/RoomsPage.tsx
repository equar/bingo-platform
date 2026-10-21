import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  ListItemText,
} from "@mui/material";
import { BingoPatternConfig, BingoRoomResponse, createRoom, listAdminRooms, updateRoom } from "../../api/admin";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { StatusState } from "../../components/StatusState";

const scheduleOptions = [
  { value: "HOURLY", label: "Hourly" },
  { value: "DAILY", label: "Daily" },
  { value: "WEEKLY", label: "Weekly" },
] as const;

const patternOptions = [
  { value: "LINE", label: "Line" },
  { value: "TWO_LINES", label: "Two lines" },
  { value: "FULL_HOUSE", label: "Full house" },
] as const;

type PatternValue = (typeof patternOptions)[number]["value"];

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
  configuredPatterns: PatternValue[];
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
  configuredPatterns: patternOptions.map((option) => option.value),
  configuredPrizes: JSON.stringify(
    [{ prizeType: "LINE", amount: 100, settlementPath: "PLAYER_WALLET", maxWinners: 1 }],
    null,
    2,
  ),
};

function isPatternValue(value: string): value is PatternValue {
  return patternOptions.some((option) => option.value === value);
}

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
    configuredPatterns: room.configuredPatterns.filter(isPatternValue),
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

function formatDraftSummary(draft: RoomDraft) {
  const patternCount = draft.configuredPatterns.length;
  const prizeCount = (() => {
    try {
      return JSON.parse(draft.configuredPrizes).length as number;
    } catch {
      return 0;
    }
  })();

  return [
    `Room: ${draft.name || "Untitled"}`,
    `Schedule: ${draft.scheduleRule}`,
    `Patterns: ${patternCount} selected`,
    `Prizes: ${prizeCount} configured`,
  ].join(" • ");
}

export function RoomsPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<RoomDraft | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingSubmit, setPendingSubmit] = useState(false);
  const [pendingError, setPendingError] = useState<string | null>(null);
  const [roomSearch, setRoomSearch] = useState("");
  const [roomStatusFilter, setRoomStatusFilter] = useState<"all" | "active" | "inactive">("all");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const normalizedSearch = roomSearch.trim();
  const roomsQuery = useQuery({
    queryKey: ["admin-rooms", normalizedSearch, roomStatusFilter, page, pageSize],
    queryFn: () =>
      listAdminRooms(
        page,
        pageSize,
        normalizedSearch || undefined,
        roomStatusFilter === "all" ? undefined : roomStatusFilter === "active",
      ),
    placeholderData: keepPreviousData,
  });

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
        configuredPatterns: body.configuredPatterns,
        configuredPrizes: parsePrizes(body.configuredPrizes),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-rooms"] });
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
        configuredPatterns: body.configuredPatterns,
        configuredPrizes: parsePrizes(body.configuredPrizes),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-rooms"] });
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

  const rooms = roomsQuery.data?.content ?? [];
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

      <Paper variant="outlined" sx={{ borderRadius: 3, overflow: "hidden" }}>
        <Box
          sx={{
            display: "flex",
            gap: 2,
            alignItems: { xs: "stretch", md: "center" },
            justifyContent: "space-between",
            flexWrap: "wrap",
            p: 2,
            borderBottom: "1px solid",
            borderColor: "divider",
            backgroundColor: "background.paper",
          }}
        >
          <TextField
            label="Search rooms"
            value={roomSearch}
            onChange={(event) => {
              setRoomSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Name, description, schedule, or pattern"
            size="small"
            sx={{ minWidth: { xs: "100%", md: 360 }, flex: 1 }}
          />
          <TextField
            select
            label="Status"
            value={roomStatusFilter}
            onChange={(event) => {
              setRoomStatusFilter(event.target.value as "all" | "active" | "inactive");
              setPage(0);
            }}
            size="small"
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="all">All rooms</MenuItem>
            <MenuItem value="active">Active only</MenuItem>
            <MenuItem value="inactive">Inactive only</MenuItem>
          </TextField>
        </Box>

        <TableContainer>
          <Table size="small" aria-label="rooms table">
            <TableHead>
              <TableRow>
                <TableCell>Room</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Price</TableCell>
                <TableCell>Capacity</TableCell>
                <TableCell>Schedule</TableCell>
                <TableCell>Patterns</TableCell>
                <TableCell>Updated</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rooms.map((room) => (
                <TableRow key={room.id} hover>
                  <TableCell>
                    <Stack spacing={0.5}>
                      <Typography variant="subtitle2">{room.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {room.description ?? "No description"}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={room.active ? "Active" : "Inactive"}
                      color={room.active ? "success" : "default"}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {room.ticketPrice} {room.currency}
                  </TableCell>
                  <TableCell>
                    {room.sessionCapacity} / {room.maxTicketsPerPlayer}
                  </TableCell>
                  <TableCell>{room.scheduleRule}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      {room.configuredPatterns.map((pattern) => (
                        <Chip key={pattern} label={pattern} size="small" />
                      ))}
                    </Stack>
                  </TableCell>
                  <TableCell>{new Date(room.updatedAt).toLocaleString()}</TableCell>
                  <TableCell align="right">
                    <Button variant="outlined" size="small" onClick={() => setDraft(roomToDraft(room))}>
                      Edit
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {roomsQuery.data && rooms.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8}>
                    <Alert severity="info" sx={{ my: 1 }}>
                      No rooms match the current filters.
                    </Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={roomsQuery.data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => {
            setPageSize(Number(event.target.value));
            setPage(0);
          }}
          rowsPerPageOptions={[10, 20, 50]}
        />
      </Paper>

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
                  select
                  label="Schedule rule"
                  value={draft?.scheduleRule ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, scheduleRule: e.target.value })}
                  fullWidth
                  helperText="Recommended preset cadence for the room."
                >
                  {scheduleOptions.map((option) => (
                    <MenuItem key={option.value} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </TextField>
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
                <FormControl fullWidth>
                  <InputLabel id="configured-patterns-label">Configured patterns</InputLabel>
                  <Select
                    labelId="configured-patterns-label"
                    label="Configured patterns"
                    multiple
                    value={draft?.configuredPatterns ?? []}
                    onChange={(event) => {
                      if (!draft) {
                        return;
                      }
                      const value = event.target.value;
                      const nextPatterns = typeof value === "string" ? value.split(",") : value;
                      setDraft({
                        ...draft,
                        configuredPatterns: nextPatterns.filter(isPatternValue),
                      });
                    }}
                    renderValue={(selected) =>
                      (selected as string[])
                        .map((pattern) => patternOptions.find((option) => option.value === pattern)?.label ?? pattern)
                        .join(", ")
                    }
                  >
                    {patternOptions.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        <Checkbox checked={(draft?.configuredPatterns ?? []).includes(option.value)} />
                        <ListItemText primary={option.label} />
                      </MenuItem>
                    ))}
                  </Select>
                  <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, ml: 1.75 }}>
                    Best practice: keep the room to a small set of patterns that match the prize structure.
                  </Typography>
                </FormControl>
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
        description={draft ? formatDraftSummary(draft) : "This will save the room configuration on the backend."}
        confirmLabel="Confirm"
        cancelLabel="Back"
        confirming={pendingSubmit}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={onSubmit}
      />
    </Stack>
  );
}
