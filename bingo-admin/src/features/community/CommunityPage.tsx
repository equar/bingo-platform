import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { http } from '../../api/http';

type Summary = { privateGames: number; dailyClaims: number; dailyPoints: number; visiblePlayers: number; timezone: string };
type PrivateGame = { roomId: number; sessionId: number; name: string; state: string; active: boolean; memberCount: number; capacity: number; scheduledStartAt: string };

export function CommunityPage() {
  const client = useQueryClient();
  const [selected, setSelected] = useState<PrivateGame | null>(null);
  const [reason, setReason] = useState('');
  const summary = useQuery({ queryKey: ['community-summary'], queryFn: async () => (await http.get<Summary>('/api/v1/admin/community/summary')).data });
  const games = useQuery({ queryKey: ['community-private-games'], queryFn: async () => (await http.get<PrivateGame[]>('/api/v1/admin/community/private-games')).data });
  const disable = useMutation({
    mutationFn: async () => { if (!selected) return; await http.post(`/api/v1/admin/community/private-games/${selected.roomId}/disable`, { reason: reason.trim() }); },
    onSuccess: async () => { setSelected(null); setReason(''); await client.invalidateQueries({ queryKey: ['community-private-games'] }); },
  });
  return <Stack spacing={3}>
    <Typography variant="h4">Player community</Typography>
    <Alert severity="info">Private games inherit approved room rules. Hosts cannot change draws or prizes. Challenges award points only; wallet balances are unaffected. Leaderboards are opt-in and use public aliases.</Alert>
    {(summary.isError || games.isError) && <Alert severity="error">Could not load community activity. <Button onClick={() => { void summary.refetch(); void games.refetch(); }}>Retry</Button></Alert>}
    {summary.isPending ? <CircularProgress aria-label="Loading community summary" /> : summary.data && <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 2 }}>
      {[
        ['Private games', summary.data.privateGames], ['Claims today', summary.data.dailyClaims], ['Points today', summary.data.dailyPoints], ['Visible ranking profiles', summary.data.visiblePlayers],
      ].map(([label, value]) => <Card key={label}><CardContent><Typography color="text.secondary">{label}</Typography><Typography variant="h4">{value}</Typography></CardContent></Card>)}
    </Box>}
    <Typography color="text.secondary">Daily challenges reset at midnight and weekly rankings begin Monday, in {summary.data?.timezone ?? 'the business timezone'}. Extra tickets do not multiply progress. Private and cancelled games never earn challenge points.</Typography>
    <Typography variant="h5">Recent private games</Typography>
    <Box sx={{ overflowX: 'auto' }}><Table aria-label="Private games"><TableHead><TableRow><TableCell>Game</TableCell><TableCell>State</TableCell><TableCell>Members</TableCell><TableCell>Planned start</TableCell><TableCell>Actions</TableCell></TableRow></TableHead><TableBody>
      {games.data?.map(game => <TableRow key={game.roomId}><TableCell><Typography>{game.name}</Typography><Typography variant="caption">Room {game.roomId}</Typography></TableCell><TableCell><Chip size="small" label={game.active ? game.state : `${game.state} · Entry disabled`} /></TableCell><TableCell>{game.memberCount}/{game.capacity}</TableCell><TableCell>{new Date(game.scheduledStartAt).toLocaleString()}</TableCell><TableCell><Stack direction="row" spacing={1}><Button component={RouterLink} to={`/sessions/${game.sessionId}`}>Session</Button><Button color="warning" disabled={!game.active} onClick={() => { setSelected(game); setReason(''); disable.reset(); }}>Disable new entry</Button></Stack></TableCell></TableRow>)}
      {games.data?.length === 0 && <TableRow><TableCell colSpan={5}>No private games yet.</TableCell></TableRow>}
    </TableBody></Table></Box>
    <Dialog open={selected !== null} onClose={() => { if (!disable.isPending) setSelected(null); }} fullWidth maxWidth="sm"><DialogTitle>Disable new entry to {selected?.name}?</DialogTitle><DialogContent><Typography sx={{ mb: 2 }}>This closes room entry and ticket sales. It does not cancel a funded game, refund tickets, reverse payouts, or change its draw. Existing claims remain subject to the normal review process.</Typography><TextField autoFocus fullWidth required label="Moderation reason" value={reason} inputProps={{ maxLength: 300 }} onChange={event => setReason(event.target.value)} />{disable.isError && <Alert severity="error">The room could not be disabled. Try again.</Alert>}</DialogContent><DialogActions><Button disabled={disable.isPending} onClick={() => setSelected(null)}>Cancel</Button><Button color="warning" variant="contained" disabled={disable.isPending || !reason.trim()} onClick={() => disable.mutate()}>Disable entry</Button></DialogActions></Dialog>
  </Stack>;
}
