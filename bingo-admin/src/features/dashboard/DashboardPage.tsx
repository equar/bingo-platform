import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import {
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  Link,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { auditLogs, dashboardMetrics, liveSessions } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus, translateAdminValue } from "../../i18n";

const staleAfterMs = 5 * 60 * 1000;

const attentionItems = [
  {
    label: "Open deposit reviews",
    action: "Review pending deposits",
    to: "/deposits?status=PENDING",
    value: (metrics: Awaited<ReturnType<typeof dashboardMetrics>>) => metrics.openDepositReviews,
  },
  {
    label: "Open win claims",
    action: "Review pending claims",
    to: "/claims?status=PENDING_REVIEW",
    value: (metrics: Awaited<ReturnType<typeof dashboardMetrics>>) => metrics.openWinClaims,
  },
  {
    label: "Open support tickets",
    action: "View support queue",
    to: "/support/tickets",
    value: (metrics: Awaited<ReturnType<typeof dashboardMetrics>>) => metrics.openSupportTickets,
  },
] as const;

function formatAmount(amount: string | null | undefined, locale: string, unavailable: string) {
  if (amount == null || String(amount).trim() === "" || !Number.isFinite(Number(amount))) {
    return unavailable;
  }
  const formatted = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    .format(Number(amount));
  return `ETB ${formatted}`;
}

function formatCount(value: number | null | undefined, locale: string, unavailable: string) {
  return typeof value === "number" && Number.isFinite(value) ? new Intl.NumberFormat(locale).format(value) : unavailable;
}

function formatTimestamp(value: string | number | null | undefined, locale: string, unavailable: string) {
  if (value == null || !Number.isFinite(new Date(value).getTime())) {
    return unavailable;
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function MetricCard({
  label,
  value,
  action,
  to,
}: {
  label: string;
  value: string | number;
  action: string;
  to: string;
}) {
  return (
    <Card elevation={0} sx={{ height: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
      <CardActionArea component={RouterLink} to={to} sx={{ height: "100%" }}>
        <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
          <Stack spacing={0.75}>
            <Typography variant="body2" color="text.secondary">
              {label}
            </Typography>
            <Typography variant="h4" fontWeight={700} lineHeight={1.15}>
              {value}
            </Typography>
            <Typography variant="caption" color="primary.main" fontWeight={600}>
              {action}
            </Typography>
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

function PanelFreshness({
  updatedAt,
  refreshing,
  stale,
  t,
  locale,
}: {
  updatedAt: number;
  refreshing: boolean;
  stale: boolean;
  t: TFunction;
  locale: string;
}) {
  return (
    <Typography variant="caption" color="text.secondary" aria-live="polite">
      {refreshing
        ? t("Refreshing")
        : stale
          ? t("Showing data last updated {{time}}", { time: formatTimestamp(updatedAt, locale, t("Unavailable")) })
          : t("Updated {{time}}", { time: formatTimestamp(updatedAt, locale, t("Unavailable")) })}
    </Typography>
  );
}

function QueryError({
  title,
  error,
  retry,
  t,
}: {
  title: string;
  error: unknown;
  retry: () => void;
  t: TFunction;
}) {
  const message = getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again.";
  return (
    <Alert
      severity="error"
      action={
        <Button color="inherit" size="small" onClick={retry}>
          {t("Retry")}
        </Button>
      }
    >
      <strong>{t(title)}.</strong> {t(message)}
    </Alert>
  );
}

export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage === "am" ? "am-ET" : "en-ET";
  const metricsQuery = useQuery({
    queryKey: ["dashboard", "metrics"],
    queryFn: dashboardMetrics,
    retry: false,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
  const sessionsQuery = useQuery({
    queryKey: ["dashboard", "live-sessions"],
    queryFn: liveSessions,
    retry: false,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
  const logsQuery = useQuery({
    queryKey: ["dashboard", "audit-logs"],
    queryFn: () => auditLogs(0, 5),
    retry: false,
    staleTime: 30_000,
  });

  if (metricsQuery.isPending && !metricsQuery.data) {
    return <StatusState title={t("Loading dashboard")} loading message={t("Fetching current operations")} />;
  }

  if (metricsQuery.isError && !metricsQuery.data) {
    return (
      <StatusState
        title={t("Unable to load dashboard")}
        message={t(getUserFacingErrorMessage(metricsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => metricsQuery.refetch()}
      />
    );
  }

  if (!metricsQuery.data) {
    return <StatusState title={t("Unable to load dashboard")} message={t("Dashboard data is unavailable.")} />;
  }

  const metrics = metricsQuery.data;
  const unavailable = t("Unavailable");
  const generatedAt = new Date(metrics.generatedAt).getTime();
  const metricsStale = !Number.isFinite(generatedAt) || Date.now() - generatedAt > staleAfterMs;

  return (
    <Stack spacing={{ xs: 2, md: 2.5 }} sx={{ maxWidth: 1440, mx: "auto" }}>
      <Box component="header">
        <Typography variant="h4" fontWeight={700} gutterBottom>
          {t("Dashboard")}
        </Typography>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={{ xs: 0.5, sm: 2 }} alignItems={{ sm: "center" }}>
          <Typography color="text.secondary">{t("A clear view of today’s operations.")}</Typography>
          <Typography variant="caption" color="text.secondary">
            {t("Data as of {{time}}", {
              time: formatTimestamp(metrics.generatedAt, locale, unavailable),
            })}
          </Typography>
        </Stack>
      </Box>

      {metricsQuery.isError ? (
        <QueryError title="Unable to refresh dashboard" error={metricsQuery.error} retry={() => metricsQuery.refetch()} t={t} />
      ) : metricsStale ? (
        <Alert severity="warning">{t("Dashboard data may be out of date.")}</Alert>
      ) : null}

      <Box component="section" aria-labelledby="needs-attention-title">
        <Stack spacing={1.25}>
          <Typography id="needs-attention-title" variant="h6" fontWeight={700}>
            {t("Needs attention")}
          </Typography>
          <Grid container spacing={1.5}>
            {attentionItems.map((item) => (
              <Grid item xs={12} sm={6} md={4} key={item.to}>
                <MetricCard
                  label={t(item.label)}
                  value={formatCount(item.value(metrics), locale, unavailable)}
                  action={t(item.action)}
                  to={item.to}
                />
              </Grid>
            ))}
          </Grid>
        </Stack>
      </Box>

      <Box component="section" aria-labelledby="operations-title">
        <Stack spacing={1.25}>
          <Typography id="operations-title" variant="h6" fontWeight={700}>
            {t("Operations")}
          </Typography>
          <Grid container spacing={1.5}>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard label={t("Active players")} value={formatCount(metrics.activePlayers, locale, unavailable)} action={t("View players")} to="/players" />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard label={t("Active agents")} value={formatCount(metrics.activeAgents, locale, unavailable)} action={t("View agents")} to="/agents" />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard label={t("Active sessions")} value={formatCount(metrics.activeSessions, locale, unavailable)} action={t("View sessions")} to="/sessions" />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                label={t("Player wallet total")}
                value={formatAmount(metrics.playerWalletBalanceTotal, locale, unavailable)}
                action={t("View players")}
                to="/players"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                label={t("Agent float available")}
                value={formatAmount(metrics.agentFloatAvailableTotal, locale, unavailable)}
                action={t("View agents")}
                to="/agents"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                label={t("Agent float clearing")}
                value={formatAmount(metrics.agentFloatClearingTotal, locale, unavailable)}
                action={t("View agents")}
                to="/agents"
              />
            </Grid>
          </Grid>
        </Stack>
      </Box>

      <Grid container spacing={1.5} alignItems="stretch">
        <Grid item xs={12} lg={6}>
          <Card
            component="section"
            aria-labelledby="live-sessions-title"
            elevation={0}
            sx={{ height: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}
          >
            <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
              <Stack spacing={1}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                  <Box>
                    <Typography id="live-sessions-title" variant="h6" fontWeight={700}>
                      {t("Live sessions")}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {t("Current games")}
                    </Typography>
                  </Box>
                  <Link component={RouterLink} to="/sessions" underline="hover" variant="body2">
                    {t("View sessions")}
                  </Link>
                </Stack>
                {sessionsQuery.data ? (
                  <PanelFreshness
                    updatedAt={sessionsQuery.dataUpdatedAt}
                    refreshing={sessionsQuery.isFetching}
                    stale={Date.now() - sessionsQuery.dataUpdatedAt > staleAfterMs}
                    t={t}
                    locale={locale}
                  />
                ) : null}
                {sessionsQuery.isError && !sessionsQuery.data ? (
                  <QueryError title="Unable to load live sessions" error={sessionsQuery.error} retry={() => sessionsQuery.refetch()} t={t} />
                ) : null}
                {sessionsQuery.isPending ? (
                  <Stack direction="row" spacing={1} alignItems="center" role="status" aria-live="polite">
                    <CircularProgress size={18} />
                    <Typography variant="body2">{t("Loading live sessions")}</Typography>
                  </Stack>
                ) : null}
                {sessionsQuery.data && sessionsQuery.data.length === 0 ? (
                  <Alert severity="info">{t("No active sessions right now.")}</Alert>
                ) : null}
                {sessionsQuery.data && sessionsQuery.data.length > 0 ? (
                  <List dense disablePadding sx={{ maxHeight: 320, overflowY: "auto" }}>
                    {sessionsQuery.data.map((session) => (
                      <ListItem
                        key={session.id}
                        divider
                        disableGutters
                        secondaryAction={                        <Chip label={session.state ? translateAdminStatus(session.state, t) : unavailable} size="small" />}
                        sx={{ pr: 11 }}
                      >
                        <ListItemText
                          primary={
                            <Link component={RouterLink} to={`/sessions/${session.id}`} underline="hover">
                              {session.sessionName || unavailable}
                            </Link>
                          }
                          secondary={t("{{room}} · {{tickets}} tickets · {{draws}} draws", {
                            room: session.roomName || unavailable,
                            tickets: session.ticketCount ?? unavailable,
                            draws: session.drawCount ?? unavailable,
                          })}
                          slotProps={{
                            primary: { component: "div", variant: "body2", fontWeight: 600, noWrap: true },
                            secondary: { component: "div", variant: "caption" },
                          }}
                        />
                      </ListItem>
                    ))}
                  </List>
                ) : null}
                {sessionsQuery.isError && sessionsQuery.data ? (
                  <Alert severity="warning">{t("Showing the last available data. Could not refresh.")}</Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={6}>
          <Card
            component="section"
            aria-labelledby="recent-activity-title"
            elevation={0}
            sx={{ height: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}
          >
            <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
              <Stack spacing={1}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                  <Box>
                    <Typography id="recent-activity-title" variant="h6" fontWeight={700}>
                      {t("Recent activity")}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {t("Latest audit entries")}
                    </Typography>
                  </Box>
                  <Link component={RouterLink} to="/audit-logs" underline="hover" variant="body2">
                    {t("View audit logs")}
                  </Link>
                </Stack>
                {logsQuery.data ? (
                  <PanelFreshness
                    updatedAt={logsQuery.dataUpdatedAt}
                    refreshing={logsQuery.isFetching}
                    stale={Date.now() - logsQuery.dataUpdatedAt > staleAfterMs}
                    t={t}
                    locale={locale}
                  />
                ) : null}
                {logsQuery.isError && !logsQuery.data ? (
                  <QueryError title="Unable to load recent activity" error={logsQuery.error} retry={() => logsQuery.refetch()} t={t} />
                ) : null}
                {logsQuery.isPending ? (
                  <Stack direction="row" spacing={1} alignItems="center" role="status" aria-live="polite">
                    <CircularProgress size={18} />
                    <Typography variant="body2">{t("Loading recent activity")}</Typography>
                  </Stack>
                ) : null}
                {logsQuery.data && logsQuery.data.content.length === 0 ? (
                  <Alert severity="info">{t("No audit history.")}</Alert>
                ) : null}
                {logsQuery.data && logsQuery.data.content.length > 0 ? (
                  <>
                    <Typography variant="caption" color="text.secondary">
                      {t("Showing {{shown}} of {{total}} entries", {
                        shown: logsQuery.data.content.length,
                        total: logsQuery.data.totalElements,
                      })}
                    </Typography>
                    <List dense disablePadding sx={{ maxHeight: 320, overflowY: "auto" }}>
                      {logsQuery.data.content.map((entry) => (
                        <ListItem key={entry.id} divider disableGutters alignItems="flex-start">
                          <ListItemText
                            primary={entry.action ? translateAdminValue(entry.action, t) : unavailable}
                            secondary={
                              <Stack component="span" spacing={0.25}>
                                <Typography component="span" variant="caption" color="text.secondary">
                                  {t("{{targetType}} · {{targetId}}", {
                                    targetType: entry.targetType ? translateAdminValue(entry.targetType, t) : unavailable,
                                    targetId: entry.targetId ?? unavailable,
                                  })}
                                </Typography>
                                {entry.reason ? (
                                  <Typography component="span" variant="caption" color="text.secondary">
                                    {entry.reason}
                                  </Typography>
                                ) : null}
                                <Typography component="span" variant="caption" color="text.secondary">
                                  {formatTimestamp(entry.createdAt, locale, unavailable)}
                                </Typography>
                              </Stack>
                            }
                            slotProps={{
                              primary: { component: "div", variant: "body2", fontWeight: 600 },
                              secondary: { component: "div", variant: "caption" },
                            }}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </>
                ) : null}
                {logsQuery.isError && logsQuery.data ? (
                  <Alert severity="warning">{t("Showing the last available data. Could not refresh.")}</Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
