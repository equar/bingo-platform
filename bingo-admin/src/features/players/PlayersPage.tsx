import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Alert, Box, Card, CardContent, Chip, Grid, Stack, TextField, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { searchPlayers } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

export function PlayersPage() {
  const [query, setQuery] = useState("");
  const playersQuery = useQuery({
    queryKey: ["players", query],
    queryFn: () => searchPlayers(query),
    enabled: query.trim().length > 1,
  });

  const loading = query.trim().length > 1 && playersQuery.isLoading;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Players</Typography>
        <Typography color="text.secondary">Search users and review account details.</Typography>
      </Box>

      <TextField
        label="Search by phone, name, or player code"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        fullWidth
      />

      {query.trim().length <= 1 ? <Alert severity="info">Enter at least two characters to search.</Alert> : null}
      {loading ? <StatusState title="Searching players" loading /> : null}
      {playersQuery.error ? (
        <StatusState
          title="Unable to load players"
          message={(playersQuery.error as Error).message}
          actionLabel="Retry"
          onAction={() => playersQuery.refetch()}
        />
      ) : null}
      {playersQuery.data && playersQuery.data.content.length === 0 ? (
        <Alert severity="info">No players match your search.</Alert>
      ) : null}

      <Grid container spacing={2}>
        {(playersQuery.data?.content ?? []).map((player) => (
          <Grid item xs={12} md={6} lg={4} key={player.id}>
            <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="h6">
                    <RouterLink to={`/players/${player.id}`}>{player.displayName}</RouterLink>
                  </Typography>
                  <Typography color="text.secondary">{player.normalizedPhone}</Typography>
                  <Typography variant="body2">Status: {player.status}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`Wallet tx ${player.walletTransactionCount}`} size="small" />
                    <Chip label={`Deposits ${player.depositClaimCount}`} size="small" />
                    <Chip label={`Support ${player.supportTicketCount}`} size="small" />
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
