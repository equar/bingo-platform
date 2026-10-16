import { Avatar, Box, Card, CardContent, Chip, Divider, Grid, Stack, Typography } from "@mui/material";
import { useMemo } from "react";
import { useSession } from "../../api/session";

export function ProfilePage() {
  const session = useSession();
  const initials = useMemo(() => session?.user.displayName?.slice(0, 1)?.toUpperCase() ?? "A", [session]);

  return (
    <Stack spacing={3}>
      <Box
        sx={{
          p: { xs: 3, md: 4 },
          borderRadius: 4,
          color: "common.white",
          background:
            "linear-gradient(135deg, rgba(11,92,171,0.98) 0%, rgba(18,62,108,0.96) 45%, rgba(10,71,132,0.94) 100%)",
          boxShadow: 3,
        }}
      >
        <Stack direction={{ xs: "column", md: "row" }} spacing={3} alignItems={{ md: "center" }}>
          <Avatar sx={{ width: 72, height: 72, bgcolor: "rgba(255,255,255,0.16)", fontSize: 30 }}>{initials}</Avatar>
          <Stack spacing={1} flexGrow={1}>
            <Typography variant="overline" sx={{ opacity: 0.8, letterSpacing: 1.5 }}>
              Admin profile
            </Typography>
            <Typography component="h1" variant="h4" fontWeight={800}>
              Profile
            </Typography>
            <Typography sx={{ maxWidth: 760, opacity: 0.9 }}>
              Review your signed-in admin account details in one focused place.
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {session?.user.roles.map((role) => (
              <Chip
                key={role}
                label={role}
                sx={{ color: "common.white", borderColor: "rgba(255,255,255,0.35)" }}
                variant="outlined"
              />
            ))}
          </Stack>
        </Stack>
      </Box>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          <Card sx={{ height: "100%", border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2.25}>
                <div>
                  <Typography variant="h6">Account details</Typography>
                  <Typography color="text.secondary">Your current admin identity and status.</Typography>
                </div>
                <Stack direction="row" spacing={2} alignItems="center">
                  <Avatar sx={{ width: 56, height: 56, bgcolor: "primary.main", fontSize: 22 }}>{initials}</Avatar>
                  <Stack spacing={0.25}>
                    <Typography variant="h6">{session?.user.displayName ?? "Account"}</Typography>
                    <Typography color="text.secondary">{session?.user.phoneNumber}</Typography>
                  </Stack>
                </Stack>
                <Divider />
                <Stack spacing={1.5}>
                  <DetailRow
                    label="Status"
                    value={
                      <Chip label={session?.user.status ?? "UNKNOWN"} size="small" color="success" variant="outlined" />
                    }
                  />
                  <DetailRow
                    label="Roles"
                    value={
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        {session?.user.roles.map((role) => (
                          <Chip key={role} label={role} size="small" />
                        ))}
                      </Stack>
                    }
                  />
                  <DetailRow label="User ID" value={<Typography>{session?.user.id ?? "-"}</Typography>} />
                  <DetailRow
                    label="Created"
                    value={<Typography>{session ? new Date(session.user.createdAt).toLocaleString() : "-"}</Typography>}
                  />
                  <DetailRow
                    label="Updated"
                    value={<Typography>{session ? new Date(session.user.updatedAt).toLocaleString() : "-"}</Typography>}
                  />
                </Stack>
                <Divider />
                <Typography color="text.secondary">
                  Use the account menu in the top-right corner for password changes and logout.
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
      <Typography color="text.secondary">{label}</Typography>
      {value}
    </Box>
  );
}