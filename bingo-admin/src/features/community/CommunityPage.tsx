import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link as RouterLink } from "react-router-dom";
import { http } from "../../api/http";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { translateAdminStatus } from "../../i18n";

type Summary = { privateGames: number; dailyClaims: number; dailyPoints: number; visiblePlayers: number; timezone: string };
type PrivateGame = { roomId: number; sessionId: number; name: string; state: string; active: boolean; memberCount: number; capacity: number; scheduledStartAt: string };

export function CommunityPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const [selected, setSelected] = useState<PrivateGame | null>(null);
  const [reason, setReason] = useState("");
  const summary = useQuery({ queryKey: ["community-summary"], queryFn: async () => (await http.get<Summary>("/api/v1/admin/community/summary")).data });
  const games = useQuery({ queryKey: ["community-private-games"], queryFn: async () => (await http.get<PrivateGame[]>("/api/v1/admin/community/private-games")).data });
  const disable = useMutation({
    mutationFn: async ({ game, reason: moderationReason }: { game: PrivateGame; reason: string }) => {
      await http.post(`/api/v1/admin/community/private-games/${game.roomId}/disable`, { reason: moderationReason });
    },
    onSuccess: async () => {
      setSelected(null);
      setReason("");
      await client.invalidateQueries({ queryKey: ["community-private-games"] });
    },
  });

  const summaryCards = summary.data
    ? [
        [t("Private games"), summary.data.privateGames],
        [t("Claims today"), summary.data.dailyClaims],
        [t("Points today"), summary.data.dailyPoints],
        [t("Visible ranking profiles"), summary.data.visiblePlayers],
      ]
    : [];

  return (
    <AdminTablePage>
    <Stack spacing={3} sx={{ minWidth: 0 }}>
      <AdminPageHeader title={t("Player community")} refresh={{
        isFetching: summary.isFetching || games.isFetching,
        failed: summary.isRefetchError || games.isRefetchError,
        error: games.error ?? summary.error,
        onRefresh: () => Promise.all([summary.refetch(), games.refetch()]),
      }} />

      <Alert severity="info">
        {t("Private games follow the approved room rules. Hosts cannot change draws or prizes. Challenges award points only and never affect wallet balances. Leaderboards are optional and show public aliases.")}
      </Alert>

      {((summary.isError && !summary.data) || (games.isError && !games.data)) && (
        <Alert severity="error" action={<Button color="inherit" onClick={() => { void summary.refetch(); void games.refetch(); }}>{t("Retry")}</Button>}>
          {t("Could not load community activity.")}
        </Alert>
      )}

      {summary.isPending ? (
        <CircularProgress aria-label={t("Loading community summary")} />
      ) : (
        <Box sx={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(160px, 100%), 1fr))", gap: 2, minWidth: 0 }}>
          {summaryCards.map(([label, value]) => (
            <Card key={String(label)} sx={{ minWidth: 0 }}>
              <CardContent>
                <Typography color="text.secondary">{label}</Typography>
                <Typography variant="h4">{value}</Typography>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      <Typography color="text.secondary">
        {t("Daily challenges reset at midnight and weekly rankings begin Monday in {{timezone}}. Extra tickets do not multiply progress. Private and cancelled games do not earn challenge points.", {
          timezone: summary.data?.timezone ?? t("the business timezone"),
        })}
      </Typography>
      <Typography variant="h5">{t("Recent private games")}</Typography>

      {games.isPending ? <CircularProgress aria-label={t("Loading private games")} /> : null}
      <Box sx={{ width: "100%", maxWidth: "100%", minWidth: 0, overflowX: "auto" }}>
        <Table size="small" aria-label={t("Private games")} sx={{ minWidth: 680 }}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Game")}</TableCell>
              <TableCell>{t("State")}</TableCell>
              <TableCell>{t("Members")}</TableCell>
              <TableCell>{t("Planned start")}</TableCell>
              <TableCell>{t("Actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {games.data?.map((game) => (
              <TableRow key={game.roomId}>
                <TableCell>
                  <Typography>{game.name}</Typography>
                  <Typography variant="caption">{t("Room")} {game.roomId}</Typography>
                </TableCell>
                <TableCell>
                  <Chip size="small" label={game.active ? translateAdminStatus(game.state, t) : t("{{state}} · Entry disabled", { state: translateAdminStatus(game.state, t) })} />
                </TableCell>
                <TableCell>{game.memberCount}/{game.capacity}</TableCell>
                <TableCell>{new Date(game.scheduledStartAt).toLocaleString()}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={1} sx={{ minWidth: "max-content" }}>
                    <Button component={RouterLink} to={`/sessions/${game.sessionId}`}>{t("View session")}</Button>
                    <Button
                      color="warning"
                      disabled={!game.active}
                      onClick={() => { setSelected(game); setReason(""); disable.reset(); }}
                    >
                      {t("Disable new entry")}
                    </Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {games.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>{t("No private games yet.")}</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>

      <Dialog
        open={selected !== null}
        onClose={() => { if (!disable.isPending) setSelected(null); }}
        fullWidth
        maxWidth="sm"
        PaperProps={{ sx: { minWidth: 0, overflowWrap: "anywhere" } }}
      >
        <DialogTitle>{t("Disable new entry to {{name}}?", { name: selected?.name ?? "" })}</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2 }}>
            {t("This closes room entry and ticket sales. It does not cancel a funded game, refund tickets, reverse payouts, or change its draw. Existing claims remain subject to normal review.")}
          </Typography>
          <TextField
            autoFocus
            fullWidth
            required
            label={t("Moderation reason")}
            value={reason}
            error={!reason.trim()}
            helperText={t("A moderation reason is required.")}
            inputProps={{ maxLength: 300 }}
            onChange={(event) => setReason(event.target.value)}
          />
          {disable.isError && <Alert severity="error" sx={{ mt: 2 }}>{t("The room could not be disabled. Try again.")}</Alert>}
        </DialogContent>
        <DialogActions sx={{ flexWrap: "wrap", gap: 1, px: 2, "& > :not(style) ~ :not(style)": { ml: 0 } }}>
          <Button disabled={disable.isPending} onClick={() => setSelected(null)}>{t("Cancel")}</Button>
          <Button
            color="warning"
            variant="contained"
            disabled={disable.isPending || !reason.trim()}
            onClick={() => {
              if (selected && reason.trim()) disable.mutate({ game: selected, reason: reason.trim() });
            }}
          >
            {disable.isPending ? t("Disabling entry…") : t("Disable entry")}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
    </AdminTablePage>
  );
}
