import { useTranslation } from "react-i18next";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Grid,
  Link,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { auditLogs, dashboardMetrics, liveSessions, reviewQueue, supportTickets } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus } from "../../i18n";

const managementShortcuts = [
  {
    title: "Admin users",
    description: "Create and maintain admin and support accounts.",
    to: "/admin-users",
  },
  {
    title: "Agents",
    description: "Review agent status, profile data, and operational actions.",
    to: "/agents",
  },
  {
    title: "Players",
    description: "Search player accounts and inspect account details.",
    to: "/players",
  },
] as const;

function MetricCard({ label, value, sublabel }: { label: string; value: string | number; sublabel?: string }) {
  return (
    <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h4">{value}</Typography>
        {sublabel ? (
          <Typography variant="body2" color="text.secondary">
            {sublabel}
          </Typography>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const { t } = useTranslation();
  const metricsQuery = useQuery({ queryKey: ["dashboard", "metrics"], queryFn: dashboardMetrics });
  const sessionsQuery = useQuery({ queryKey: ["dashboard", "live-sessions"], queryFn: liveSessions });
  const queueQuery = useQuery({ queryKey: ["dashboard", "review-queue"], queryFn: () => reviewQueue(0, 5) });
  const logsQuery = useQuery({ queryKey: ["dashboard", "audit-logs"], queryFn: () => auditLogs(0, 5) });
  const supportPriorityQuery = useQuery({
    queryKey: ["dashboard", "support-priorities"],
    queryFn: () => supportTickets(undefined, undefined, undefined, undefined, undefined, 0, 100),
  });
  const supportPriorityCounts = useMemo(() => {
    const counts: Record<string, number> = { LOW: 0, NORMAL: 0, HIGH: 0, URGENT: 0 };
    (supportPriorityQuery.data?.content ?? []).forEach((ticket) => {
      counts[ticket.priority] = (counts[ticket.priority] ?? 0) + 1;
    });
    return counts;
  }, [supportPriorityQuery.data]);

  if (metricsQuery.isLoading) {
    return <StatusState title={t("Loading dashboard")} loading message={t("Fetching live metrics and activity")} />;
  }

  if (metricsQuery.error) {
    return (
      <StatusState
        title={t("Unable to load dashboard")}
        message={t(getUserFacingErrorMessage(metricsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => metricsQuery.refetch()}
      />
    );
  }

  const metrics = metricsQuery.data!;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" gutterBottom>
          {t("Dashboard")}</Typography>
        <Typography color="text.secondary">{t("Operational overview powered by the backend admin contract.")}</Typography>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard label={t("Active players")} value={metrics.activePlayers} />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard label={t("Active sessions")} value={metrics.activeSessions} />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard label={t("Open deposit reviews")} value={metrics.openDepositReviews} />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard label={t("Open win claims")} value={metrics.openWinClaims} />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard label={t("Open support tickets")} value={metrics.openSupportTickets} />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard label={t("Active agents")} value={metrics.activeAgents} />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard
            label={t("Player wallet total")}
            value={metrics.playerWalletBalanceTotal}
            sublabel={metrics.businessTimezone}
          />
        </Grid>
        <Grid item xs={12} md={4} lg={3}>
          <MetricCard
            label={t("Agent float total")}
            value={metrics.agentFloatAvailableTotal}
            sublabel={t("Clearing {{amount}}", { amount: metrics.agentFloatClearingTotal })}
          />
        </Grid>
      </Grid>

      <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">{t("Management shortcuts")}</Typography>
              <Typography variant="body2" color="text.secondary">
                {t("The dashboard now includes the old overview entry points so operators can start from one place.")}</Typography>
            </Box>
            <Grid container spacing={2}>
              {managementShortcuts.map((section) => (
                <Grid item xs={12} md={4} key={section.to}>
                  <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
                    <CardActionArea component={RouterLink} to={section.to} sx={{ height: "100%" }}>
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography variant="h6">{t(section.title)}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {t(section.description)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">{t("Support priority counts")}</Typography>
            <Typography variant="body2" color="text.secondary">
              {t("These counts are derived from the loaded ticket page because the backend does not expose aggregate priority totals.")}</Typography>
            <Grid container spacing={2}>
              {(["LOW", "NORMAL", "HIGH", "URGENT"] as const).map((priority) => (
                <Grid item xs={12} sm={6} md={3} key={priority}>
                  <Card variant="outlined">
                    <CardContent>
                      <Typography variant="overline" color="text.secondary">
                        {t(priority)}
                      </Typography>
                      <Typography variant="h4">{supportPriorityCounts[priority] ?? 0}</Typography>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {t("Live sessions")}</Typography>
              {sessionsQuery.isLoading ? <StatusState title={t("Loading live sessions")} loading /> : null}
              {sessionsQuery.data && sessionsQuery.data.length === 0 ? (
                <Alert severity="info">{t("No active sessions right now.")}</Alert>
              ) : null}
              <List dense>
                {(sessionsQuery.data ?? []).map((session) => (
                  <ListItem key={session.id} divider secondaryAction={<Chip label={translateAdminStatus(session.state, t)} size="small" />}>
                    <ListItemText
                      primary={
                        <Link component={RouterLink} to={`/sessions/${session.id}`} underline="hover">
                          {session.sessionName}
                        </Link>
                      }
                      secondary={t("{{room}} · {{tickets}} tickets · {{draws}} draws", {
                        room: session.roomName,
                        tickets: session.ticketCount,
                        draws: session.drawCount,
                      })}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={6}>
          <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {t("Activity")}</Typography>
              <Stack spacing={2}>
                {queueQuery.data ? (
                  <Box>
                    <Typography variant="subtitle2">{t("Review queue")}</Typography>
                    {queueQuery.data.content.length === 0 ? (
                      <Typography color="text.secondary">{t("No items in the queue.")}</Typography>
                    ) : null}
                    {queueQuery.data.content.slice(0, 5).map((item) => (
                      <Stack key={item.id} direction="row" spacing={1} sx={{ py: 0.75 }} justifyContent="space-between">
                        <Typography variant="body2">
                          {item.queueType}{t(":")}{" "}{item.title}
                        </Typography>
                        <Chip label={translateAdminStatus(item.status, t)} size="small" />
                      </Stack>
                    ))}
                  </Box>
                ) : null}
                {logsQuery.data ? (
                  <Box>
                    <Typography variant="subtitle2">{t("Recent audit logs")}</Typography>
                    {logsQuery.data.content.length === 0 ? (
                      <Typography color="text.secondary">{t("No audit history.")}</Typography>
                    ) : null}
                    {logsQuery.data.content.map((entry) => (
                      <Stack
                        key={entry.id}
                        spacing={0.25}
                        sx={{ py: 1, borderBottom: "1px solid", borderColor: "divider" }}
                      >
                        <Typography variant="body2" fontWeight={600}>
                          {entry.action}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {entry.targetType} {entry.targetId ?? t("n/a")} {t("·")}{" "}{entry.reason ?? t("no reason")}
                        </Typography>
                      </Stack>
                    ))}
                  </Box>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
