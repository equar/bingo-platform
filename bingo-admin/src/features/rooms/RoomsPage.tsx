import { useTranslation } from "react-i18next";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from "@mui/material";
import { BingoRoomResponse, createRoom, getAdminRoom, listAdminRooms, updateRoom } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { AdminTablePage } from "../../components/AdminTablePage";
import { translateAdminValue } from "../../i18n";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";

const scheduleOptions = [
  { value: "HOURLY", label: "Hourly" },
  { value: "DAILY", label: "Daily" },
  { value: "WEEKLY", label: "Weekly" },
] as const;

const patternOptions = [
  { value: "LINE", label: "Line" },
  { value: "TWO_LINES", label: "Two lines" },
  { value: "FOUR_CORNERS", label: "Four corners" },
  { value: "POSTAGE_STAMP", label: "Postage stamp" },
  { value: "X_SHAPE", label: "X shape" },
  { value: "T_SHAPE", label: "T shape" },
  { value: "L_SHAPE", label: "L shape" },
  { value: "OUTER_FRAME", label: "Outer frame" },
  { value: "FULL_HOUSE", label: "Full house" },
] as const;

type PatternValue = (typeof patternOptions)[number]["value"];
type SettlementPath = "PLAYER_WALLET" | "AGENT_CASH";

interface PrizeDraft {
  prizeType: PatternValue;
  amount: string;
  settlementPath: SettlementPath;
  maxWinners: string;
}

interface RoomDraft {
  id?: number;
  version?: number;
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
  configuredPrizes: PrizeDraft[];
}

const emptyDraft: RoomDraft = {
  name: "",
  description: "",
  ticketPrice: "0.01",
  currency: "ETB",
  timezone: "Africa/Addis_Ababa",
  scheduleRule: "WEEKLY",
  sessionCapacity: "100",
  maxTicketsPerPlayer: "5",
  ticketSalesCloseOnSessionStart: true,
  allowMultipleWinnersPerPrize: false,
  active: true,
  configuredPrizes: [{ prizeType: "LINE", amount: "100", settlementPath: "PLAYER_WALLET", maxWinners: "1" }],
};

function isPatternValue(value: string): value is PatternValue {
  return patternOptions.some((option) => option.value === value);
}

function roomToDraft(room: BingoRoomResponse): RoomDraft {
  return {
    id: room.id,
    version: room.version,
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
    configuredPrizes: room.configuredPrizes
      .filter((prize) => isPatternValue(prize.prizeType))
      .map((prize) => ({
        prizeType: prize.prizeType as PatternValue,
        amount: String(prize.amount),
        settlementPath: prize.settlementPath === "AGENT_CASH" ? "AGENT_CASH" : "PLAYER_WALLET",
        maxWinners: String(prize.maxWinners ?? 1),
      })),
  };
}

function roomDraftsEqual(left: RoomDraft, right: RoomDraft) {
  return left.name === right.name
    && left.description === right.description
    && left.ticketPrice === right.ticketPrice
    && left.currency === right.currency
    && left.timezone === right.timezone
    && left.scheduleRule === right.scheduleRule
    && left.sessionCapacity === right.sessionCapacity
    && left.maxTicketsPerPlayer === right.maxTicketsPerPlayer
    && left.ticketSalesCloseOnSessionStart === right.ticketSalesCloseOnSessionStart
    && left.allowMultipleWinnersPerPrize === right.allowMultipleWinnersPerPrize
    && left.active === right.active
    && JSON.stringify(left.configuredPrizes) === JSON.stringify(right.configuredPrizes);
}

function validateRoomDraft(draft: RoomDraft): string | null {
  const ticketPrice = Number(draft.ticketPrice);
  if (!Number.isFinite(ticketPrice) || ticketPrice < 0.01 || !/^\d+(\.\d{1,2})?$/.test(draft.ticketPrice)) {
    return "Ticket price must be at least 0.01 and have no more than two decimal places.";
  }
  if (draft.configuredPrizes.length === 0) {
    return "Add at least one prize pattern.";
  }
  if (new Set(draft.configuredPrizes.map((prize) => prize.prizeType)).size !== draft.configuredPrizes.length) {
    return "Each pattern can only have one prize configuration.";
  }
  if (draft.configuredPrizes.some((prize) => !/^\d+(\.\d{1,2})?$/.test(prize.amount)
      || !Number.isFinite(Number(prize.amount)) || Number(prize.amount) < 0.01
      || !/^\d+$/.test(prize.maxWinners) || Number(prize.maxWinners) < 1)) {
    return "Each prize needs an amount of at least 0.01 and at least one winner.";
  }
  return null;
}

function formatDraftSummary(draft: RoomDraft, t: (key: string, options?: Record<string, unknown>) => string) {
  const prizeCount = draft.configuredPrizes.length;

  return [
    t("Room: {{name}}", { name: draft.name || t("Untitled") }),
    t("Schedule: {{rule}}", { rule: draft.scheduleRule }),
    t("Prize patterns: {{count}} configured", { count: prizeCount }),
  ].join(` ${t("·")} `);
}

function EditorSurface({ children }: { children: ReactNode }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 3,
        overflow: "hidden",
        width: "100%",
        maxWidth: 1200,
        mx: "auto",
        bgcolor: "background.paper",
      }}
    >
      {children}
    </Paper>
  );
}

export function RoomsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { roomId: roomIdParam } = useParams<{ roomId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const isCreatePage = location.pathname.endsWith("/rooms/new");
  const isEditPage = location.pathname.endsWith("/edit") && roomIdParam !== undefined;
  const isEditorPage = isCreatePage || isEditPage;
  const roomId = Number(roomIdParam);
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<RoomDraft | null>(() => isCreatePage ? { ...emptyDraft } : null);
  const [originalDraft, setOriginalDraft] = useState<RoomDraft | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  const [pendingSubmit, setPendingSubmit] = useState(false);
  const [pendingError, setPendingError] = useState<string | null>(null);
  const [roomSearch, setRoomSearch] = usePrivateListSearch("rooms");
  const roomStatusValue = searchParams.get("status") ?? "all";
  const roomStatusFilter = ["all", "active", "inactive"].includes(roomStatusValue) ? roomStatusValue as "all" | "active" | "inactive" : "all";
  const pageValue = Number(searchParams.get("page") ?? 0);
  const page = Number.isSafeInteger(pageValue) ? Math.max(0, pageValue) : 0;
  const sizeValue = Number(searchParams.get("size") ?? 20);
  const pageSize = [10, 20, 50].includes(sizeValue) ? sizeValue : 20;
  const updateListState = useCallback((updates: Record<string, string>) => setSearchParams((current) => {
    const next = new URLSearchParams(current);
    Object.entries(updates).forEach(([key, value]) => value && value !== "all" ? next.set(key, value) : next.delete(key));
    return next;
  }, { replace: true }), [setSearchParams]);
  const roomDetailQuery = useQuery({
    queryKey: ["admin-room", roomId],
    queryFn: () => getAdminRoom(roomId),
    enabled: isEditPage && Number.isInteger(roomId) && roomId > 0,
  });
  useEffect(() => {
    if (isCreatePage) {
      setDraft({ ...emptyDraft });
      setOriginalDraft(null);
      setPendingError(null);
    } else if (isEditPage && roomDetailQuery.data) {
      const nextDraft = roomToDraft(roomDetailQuery.data);
      setDraft(nextDraft);
      setOriginalDraft(nextDraft);
      setPendingError(null);
    }
  }, [isCreatePage, isEditPage, roomDetailQuery.data]);
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
        configuredPatterns: body.configuredPrizes.map((prize) => prize.prizeType),
        configuredPrizes: body.configuredPrizes.map((prize) => ({
          prizeType: prize.prizeType,
          amount: Number(prize.amount),
          settlementPath: prize.settlementPath,
          maxWinners: Number(prize.maxWinners),
        })),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-rooms"] });
      if (isEditorPage) navigate(`/rooms${location.search}`);
      setDraft(null);
      setOriginalDraft(null);
      setConfirmOpen(false);
      setPendingError(null);
    },
    onError: (error) => setPendingError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
    onSettled: () => setPendingSubmit(false),
  });

  const updateMutation = useMutation({
    mutationFn: (body: RoomDraft) =>
      updateRoom(body.id!, {
        version: body.version,
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
        configuredPatterns: body.configuredPrizes.map((prize) => prize.prizeType),
        configuredPrizes: body.configuredPrizes.map((prize) => ({
          prizeType: prize.prizeType,
          amount: Number(prize.amount),
          settlementPath: prize.settlementPath,
          maxWinners: Number(prize.maxWinners),
        })),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-rooms"] });
      await queryClient.invalidateQueries({ queryKey: ["admin-room", roomId] });
      navigate(`/rooms${location.search}`);
      setDraft(null);
      setOriginalDraft(null);
      setConfirmOpen(false);
      setPendingError(null);
    },
    onError: (error) => setPendingError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
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
  const title = draft?.id ? "Edit room" : "Create room";
  const hasDraftChanges = draft !== null && (
    draft.id === undefined
      || (originalDraft !== null && !roomDraftsEqual(draft, originalDraft))
  );

  const updatePrize = (index: number, changes: Partial<PrizeDraft>) => {
    setDraft((current) => current ? {
      ...current,
      configuredPrizes: current.configuredPrizes.map((prize, prizeIndex) =>
        prizeIndex === index ? { ...prize, ...changes } : prize),
    } : current);
  };

  const addPrize = () => {
    setDraft((current) => {
      if (!current) return current;
      const nextPattern = patternOptions.find((option) =>
        !current.configuredPrizes.some((prize) => prize.prizeType === option.value));
      if (!nextPattern) return current;
      return {
        ...current,
        configuredPrizes: [...current.configuredPrizes, {
          prizeType: nextPattern.value,
          amount: "100",
          settlementPath: "PLAYER_WALLET",
          maxWinners: "1",
        }],
      };
    });
  };

  if (roomsQuery.isLoading || (isEditPage && roomDetailQuery.isLoading)) {
    return <StatusState title={t("Loading rooms")} loading message={t("Fetching room catalog")} />;
  }

  if ((roomsQuery.error && !roomsQuery.data) || (isEditPage && roomDetailQuery.error)) {
    return (
      <StatusState
        title={t("Unable to load rooms")}
        message={t(getUserFacingErrorMessage(roomDetailQuery.error ?? roomsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => roomsQuery.refetch()}
      />
    );
  }

  return (
    <AdminTablePage enabled={!isEditorPage}>
    <Stack spacing={3}>
      {!isEditorPage ? <AdminPageHeader title={t("Rooms")} description={t("Create and edit bingo room configuration and activation.")} refresh={{ isFetching: roomsQuery.isFetching, failed: roomsQuery.isRefetchError, error: roomsQuery.error, onRefresh: () => roomsQuery.refetch() }} actions={
        <Button variant="contained" onClick={() => navigate(`/rooms/new${location.search}`)}>{t("Create room")}</Button>
      } /> : null}

      {!isEditorPage ? <Paper variant="outlined" sx={{ borderRadius: 3, overflow: "hidden" }}>
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
            label={t("Search rooms")}
            value={roomSearch}
            onChange={(event) => {
              setRoomSearch(event.target.value);
              updateListState({ page: "" });
            }}
            placeholder={t("Name, description, schedule, or pattern")}
            size="small"
            sx={{ minWidth: { xs: "100%", md: 360 }, flex: 1 }}
          />
          <TextField
            select
            label={t("Status")}
            value={roomStatusFilter}
            onChange={(event) => {
              updateListState({ status: event.target.value, page: "" });
            }}
            size="small"
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="all">{t("All rooms")}</MenuItem>
            <MenuItem value="active">{t("Active only")}</MenuItem>
            <MenuItem value="inactive">{t("Inactive only")}</MenuItem>
          </TextField>
        </Box>

        <TableContainer>
          <Table size="small" aria-label={t("rooms table")}>
            <TableHead>
              <TableRow>
                <TableCell>{t("Room")}</TableCell>
                <TableCell>{t("Status")}</TableCell>
                <TableCell>{t("Price")}</TableCell>
                <TableCell>{t("Capacity")}</TableCell>
                <TableCell>{t("Suggested cadence")}</TableCell>
                <TableCell>{t("Patterns")}</TableCell>
                <TableCell>{t("Updated")}</TableCell>
                <TableCell align="right">{t("Actions")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rooms.map((room) => (
                <TableRow key={room.id} hover>
                  <TableCell>
                    <Stack spacing={0.5}>
                      <Typography variant="subtitle2">{room.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {room.description ?? t("No description")}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={room.active ? t("Active") : t("Inactive")}
                      color={room.active ? "success" : "default"}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {room.ticketPrice} {room.currency}
                  </TableCell>
                  <TableCell>
                    {room.sessionCapacity} {t("/")}{" "}{room.maxTicketsPerPlayer}
                  </TableCell>
                  <TableCell>{t(scheduleOptions.find((option) => option.value === room.scheduleRule)?.label ?? room.scheduleRule)}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      {room.configuredPrizes.map((prize) => (
                        <Chip key={prize.prizeType} label={translateAdminValue(prize.prizeType, t)} size="small" />
                      ))}
                    </Stack>
                  </TableCell>
                  <TableCell>{new Date(room.updatedAt).toLocaleString()}</TableCell>
                  <TableCell align="right">
                    <Button
                      variant="outlined"
                      size="small"
                      onClick={() => navigate(`/rooms/${room.id}/edit${location.search}`)}
                    >
                      {t("Edit")}</Button>
                  </TableCell>
                </TableRow>
              ))}
              {roomsQuery.data && rooms.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8}>
                    <Alert severity="info" sx={{ my: 1 }}>
                      {t("No rooms match the current filters.")}</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
        <AdminTablePagination
          component="div"
          dataLoaded={!!roomsQuery.data}
          count={roomsQuery.data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, nextPage) => updateListState({ page: String(nextPage) })}
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => {
            updateListState({ size: event.target.value, page: "" });
          }}
          rowsPerPageOptions={[10, 20, 50]}
        />
      </Paper> : null}

      {isEditorPage ? (
        <>
          <EditorSurface>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, px: { xs: 2, md: 3 }, py: 1.5, borderBottom: "1px solid", borderColor: "divider" }}>
          <Button variant="text" onClick={() => navigate(`/rooms${location.search}`)} sx={{ minWidth: 0, px: 0 }}>
            {t("← Rooms")}
          </Button>
          <Typography variant="h5">{isEditPage ? t("Edit room") : t("Create room")}</Typography>
        </Box>
        <Box sx={{ px: { xs: 2, md: 3 }, py: { xs: 2, md: 2.5 } }}>
          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={isEditPage ? 9 : 12}>
            <Stack spacing={2.5}>
            {pendingError ? <Alert severity="error">{t(pendingError)}</Alert> : null}
            <Grid container spacing={1.5}>
              <Grid item xs={12} md={6}>
                <TextField
                  label={t("Room name")}
                  value={draft?.name ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, name: e.target.value })}
                  fullWidth
                  required
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label={t("Currency")}
                  value={draft?.currency ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, currency: e.target.value.toUpperCase() })}
                  fullWidth
                  required
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label={t("Description")}
                  value={draft?.description ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, description: e.target.value })}
                  fullWidth
                  multiline
                  minRows={2}
                />
              </Grid>
              <Grid item xs={12}>
                <Typography variant="h6" sx={{ mt: 0.5 }}>{t("Ticket and session settings")}</Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label={t("Ticket price")}
                  value={draft?.ticketPrice ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, ticketPrice: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label={t("Timezone")}
                  value={draft?.timezone ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, timezone: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  select
                  label={t("Suggested session cadence")}
                  value={draft?.scheduleRule ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, scheduleRule: e.target.value })}
                  fullWidth
                >
                  {draft?.scheduleRule && !scheduleOptions.some((option) => option.value === draft.scheduleRule) ? (
                    <MenuItem value={draft.scheduleRule}>{t("Existing custom cadence")}: {draft.scheduleRule}</MenuItem>
                  ) : null}
                  {scheduleOptions.map((option) => (
                    <MenuItem key={option.value} value={option.value}>
                      {t(option.label)}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label={t("Session capacity")}
                  value={draft?.sessionCapacity ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, sessionCapacity: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label={t("Max tickets per player")}
                  value={draft?.maxTicketsPerPlayer ?? ""}
                  onChange={(e) => draft && setDraft({ ...draft, maxTicketsPerPlayer: e.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12}>
                <Stack spacing={1.25}>
                  <Box display="flex" alignItems="center" justifyContent="space-between" gap={2} sx={{ mt: 0.5 }}>
                    <div>
                      <Typography variant="h6">{t("Prize setup")}</Typography>
                    </div>
                    <Button
                      size="small"
                      onClick={addPrize}
                      disabled={!draft || draft.configuredPrizes.length >= patternOptions.length}
                    >{t("Add prize")}</Button>
                  </Box>
                  <Stack spacing={1.5}>
                    {(draft?.configuredPrizes ?? []).map((prize, index) => (
                      <Paper key={`${prize.prizeType}-${index}`} variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                        <Stack spacing={1}>
                          <Box display="flex" alignItems="center" justifyContent="space-between">
                            <Typography variant="subtitle2">{t("Prize {{number}}", { number: index + 1 })}</Typography>
                            <Button
                              size="small"
                              color="error"
                              disabled={(draft?.configuredPrizes.length ?? 0) <= 1}
                              onClick={() => setDraft((current) => current ? {
                                ...current,
                                configuredPrizes: current.configuredPrizes.filter((_, prizeIndex) => prizeIndex !== index),
                              } : current)}
                            >{t("Remove")}</Button>
                          </Box>
                          <Grid container spacing={1.25}>
                            <Grid item xs={12} sm={6}>
                              <TextField select size="small" label={t("Pattern")} value={prize.prizeType}
                                onChange={(event) => updatePrize(index, { prizeType: event.target.value as PatternValue })} fullWidth>
                                {patternOptions.map((option) => (
                                  <MenuItem key={option.value} value={option.value}
                                    disabled={(draft?.configuredPrizes ?? []).some((other, otherIndex) => otherIndex !== index && other.prizeType === option.value)}>
                                    {t(option.label)}
                                  </MenuItem>
                                ))}
                              </TextField>
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField size="small" label={t("Prize amount")} type="number" value={prize.amount}
                                onChange={(event) => updatePrize(index, { amount: event.target.value })}
                                inputProps={{ min: 0.01, step: 0.01 }} fullWidth />
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField select size="small" label={t("Settlement")} value={prize.settlementPath}
                                onChange={(event) => updatePrize(index, { settlementPath: event.target.value as SettlementPath })} fullWidth>
                                <MenuItem value="PLAYER_WALLET">{t("Player wallet")}</MenuItem>
                                <MenuItem value="AGENT_CASH">{t("Agent cash")}</MenuItem>
                              </TextField>
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField size="small" label={t("Max winners")} type="number" value={prize.maxWinners}
                                onChange={(event) => updatePrize(index, { maxWinners: event.target.value })}
                                inputProps={{ min: 1, step: 1 }} disabled={!draft?.allowMultipleWinnersPerPrize} fullWidth />
                            </Grid>
                          </Grid>
                        </Stack>
                      </Paper>
                    ))}
                  </Stack>
                </Stack>
              </Grid>
            </Grid>
            <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
              <Typography variant="h6" sx={{ mb: 0.5 }}>{t("Room rules")}</Typography>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={draft?.ticketSalesCloseOnSessionStart ?? false}
                    onChange={(e) => draft && setDraft({ ...draft, ticketSalesCloseOnSessionStart: e.target.checked })}
                  />
                }
                label={t("Close ticket sales when the session starts")}
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={draft?.allowMultipleWinnersPerPrize ?? false}
                    onChange={(e) => draft && setDraft({ ...draft, allowMultipleWinnersPerPrize: e.target.checked })}
                  />
                }
                label={t("Allow multiple winners per prize")}
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={draft?.active ?? false}
                    onChange={(e) => draft && setDraft({ ...draft, active: e.target.checked })}
                  />
                }
                label={t("Room is active")}
              />
            </Paper>
            </Stack>
            </Grid>
            {isEditPage ? <Grid item xs={12} lg={3}>
              <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2, position: { lg: "sticky" }, top: 16 }}>
                <Typography variant="subtitle1" sx={{ mb: 1 }}>{t("Room history")}</Typography>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">{t("Created by")}</Typography>
                    <Typography>{roomDetailQuery.data?.createdByDisplayName || (isCreatePage ? t("Recorded when saved") : t("Not recorded"))}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">{t("Created at")}</Typography>
                    <Typography>{roomDetailQuery.data?.createdAt ? new Date(roomDetailQuery.data.createdAt).toLocaleString() : t("Available after creation")}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">{t("Updated by")}</Typography>
                    <Typography>{roomDetailQuery.data?.updatedByDisplayName || (isCreatePage ? t("Recorded when saved") : t("Not recorded"))}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">{t("Updated at")}</Typography>
                    <Typography>{roomDetailQuery.data?.updatedAt ? new Date(roomDetailQuery.data.updatedAt).toLocaleString() : t("Available after creation")}</Typography>
                  </Box>
                </Stack>
              </Paper>
            </Grid> : null}
          </Grid>
        </Box>
        <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, px: { xs: 2, md: 3 }, py: 1.5, borderTop: "1px solid", borderColor: "divider", bgcolor: "grey.50" }}>
          <Button
            onClick={() => {
              if (hasDraftChanges) setDiscardOpen(true);
              else navigate(`/rooms${location.search}`);
            }}
            disabled={pendingSubmit}
          >
            {t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={() => {
              if (!draft) return;
              const validationError = validateRoomDraft(draft);
              if (validationError) {
                setPendingError(validationError);
                return;
              }
              setPendingError(null);
              setConfirmOpen(true);
            }}
            disabled={pendingSubmit || !hasDraftChanges}
          >
            {t(isEditPage ? "Save changes" : "Create room")}</Button>
        </Box>
          </EditorSurface>
        </>
      ) : null}

      <ConfirmDialog
        open={confirmOpen}
        title={title}
        description={draft ? formatDraftSummary(draft, t) : t("This will save the room configuration on the backend.")}
        confirmLabel={t("Confirm")}
        cancelLabel={t("Back")}
        confirming={pendingSubmit}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={onSubmit}
      />
      <ConfirmDialog
        open={discardOpen}
        title={t("Discard unsaved changes?")}
        description={t("Your changes have not been saved. Discard them?")}
        confirmLabel={t("Discard")}
        cancelLabel={t("Keep editing")}
        onCancel={() => setDiscardOpen(false)}
        onConfirm={() => navigate(`/rooms${location.search}`)}
      />
    </Stack>
    </AdminTablePage>
  );
}
