import { useTranslation } from "react-i18next";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
    to: "/deposits?status=PENDING",
    value: (metrics: Awaited<ReturnType<typeof dashboardMetrics>>) => metrics.openDepositReviews,
  },
  {
    label: "Open win claims",
    to: "/claims?status=PENDING_REVIEW",
    value: (metrics: Awaited<ReturnType<typeof dashboardMetrics>>) => metrics.openWinClaims,
  },
  {
    label: "Open support tickets",
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
  to,
}: {
  label: string;
  value: string | number;
  to: string;
}) {
  return (
    <Card elevation={0} sx={{ height: "100%", minWidth: 0, border: "1px solid", borderColor: "divider", borderRadius: "12px" }}>
      <CardActionArea component={RouterLink} to={to} sx={{ height: "100%", minWidth: 0 }}>
        <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
          <Stack spacing={0.5} sx={{ minWidth: 0 }}>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>
              {label}
            </Typography>
            <Typography variant="h6" fontWeight={700} lineHeight={1.15} sx={{ overflowWrap: "anywhere" }}>
              {value}
            </Typography>
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
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
  const queryClient = useQueryClient();
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
    queryFn: () => auditLogs(0, 3),
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
  const refreshing = metricsQuery.isFetching || sessionsQuery.isFetching || logsQuery.isFetching;
  const visibleSessions = sessionsQuery.data?.slice(0, 3) ?? [];
  const visibleActivity = logsQuery.data?.content.slice(0, 3) ?? [];

  return (
    <Stack data-testid="dashboard-content" spacing={{ xs: 1.5, md: 2 }} sx={{ width: "100%", minWidth: 0 }}>
      <Box component="header">
        <Stack
          direction={{ xs: "column", sm: "row" }}
          justifyContent="space-between"
          alignItems={{ xs: "flex-start", sm: "center" }}
          gap={1}
        >
          <Stack spacing={0.25}>
            <Typography variant="h4" fontWeight={700} lineHeight={1.15}>
              {t("Dashboard")}
            </Typography>
            <Typography color="text.secondary">{t("A clear view of today’s operations.")}</Typography>
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "flex-start", sm: "center" }}>
            <Typography variant="caption" color="text.secondary">
              {t("Data as of {{time}}", {
                time: formatTimestamp(metrics.generatedAt, locale, unavailable),
              })}
            </Typography>
            <Button
              size="small"
              variant="outlined"
              aria-label={t("Refresh dashboard")}
              aria-busy={refreshing}
              disabled={refreshing}
              startIcon={refreshing ? <CircularProgress size={14} color="inherit" /> : undefined}
              onClick={() => {
                void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
              }}
            >
              {t("Refresh dashboard")}
            </Button>
          </Stack>
        </Stack>
      </Box>

      {metricsQuery.isError ? (
        <QueryError
          title="Unable to refresh dashboard"
          error={metricsQuery.error}
          retry={() => metricsQuery.refetch()}
          t={t}
        />
      ) : metricsStale ? (
        <Alert severity="warning">{t("Dashboard data may be out of date.")}</Alert>
      ) : null}

      <Box component="section" aria-labelledby="needs-attention-title">
        <Stack spacing={1}>
          <Typography id="needs-attention-title" variant="h6" fontWeight={700}>
            {t("Needs attention")}
          </Typography>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "minmax(0, 1fr)",
                sm: "repeat(2, minmax(0, 1fr))",
                md: "repeat(3, minmax(0, 1fr))",
              },
              gap: 1.5,
              minWidth: 0,
            }}
          >
            {attentionItems.map((item) => (
              <Box key={item.to} sx={{ minWidth: 0 }}>
                <MetricCard
                  label={t(item.label)}
                  value={formatCount(item.value(metrics), locale, unavailable)}
                  to={item.to}
                />
              </Box>
            ))}
          </Box>
        </Stack>
      </Box>

      <Box component="section" aria-labelledby="operations-title">
        <Stack spacing={1}>
          <Typography id="operations-title" variant="h6" fontWeight={700}>
            {t("Operations")}
          </Typography>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "minmax(0, 1fr)",
                sm: "repeat(2, minmax(0, 1fr))",
                md: "repeat(3, minmax(0, 1fr))",
                lg: "repeat(6, minmax(0, 1fr))",
              },
              gap: 1.5,
              minWidth: 0,
            }}
          >
            <MetricCard label={t("Active players")} value={formatCount(metrics.activePlayers, locale, unavailable)} to="/players" />
            <MetricCard label={t("Active agents")} value={formatCount(metrics.activeAgents, locale, unavailable)} to="/agents" />
            <MetricCard label={t("Active sessions")} value={formatCount(metrics.activeSessions, locale, unavailable)} to="/sessions" />
            <MetricCard
              label={t("Player wallet total")}
              value={formatAmount(metrics.playerWalletBalanceTotal, locale, unavailable)}
              to="/players"
            />
            <MetricCard
              label={t("Agent float available")}
              value={formatAmount(metrics.agentFloatAvailableTotal, locale, unavailable)}
              to="/agents"
            />
            <MetricCard
              label={t("Agent float clearing")}
              value={formatAmount(metrics.agentFloatClearingTotal, locale, unavailable)}
              to="/agents"
            />
          </Box>
        </Stack>
      </Box>

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "minmax(0, 1fr)", lg: "repeat(2, minmax(0, 1fr))" },
          gap: 1.5,
          minWidth: 0,
          alignItems: "start",
        }}
      >
          <Card
            component="section"
            aria-labelledby="live-sessions-title"
            elevation={0}
            sx={{ minWidth: 0, border: "1px solid", borderColor: "divider", borderRadius: "12px" }}
          >
            <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
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
                {sessionsQuery.isError && !sessionsQuery.data ? (
                  <QueryError
                    title="Unable to load live sessions"
                    error={sessionsQuery.error}
                    retry={() => sessionsQuery.refetch()}
                    t={t}
                  />
                ) : null}
                {sessionsQuery.isPending ? (
                  <Stack direction="row" spacing={1} alignItems="center" role="status" aria-live="polite">
                    <CircularProgress size={18} />
                    <Typography variant="body2">{t("Loading live sessions")}</Typography>
                  </Stack>
                ) : null}
                {sessionsQuery.data && sessionsQuery.data.length === 0 ? (
                  <Alert severity="info" sx={{ py: 0.25, "& .MuiAlert-message": { py: 0.25 } }}>
                    {t("No active sessions right now.")}
                  </Alert>
                ) : null}
                {sessionsQuery.data && sessionsQuery.data.length > 0 ? (
                  <>
                    {sessionsQuery.data.length > visibleSessions.length ? (
                      <Typography variant="caption" color="text.secondary">
                        {t("Showing {{shown}} of {{total}} listed sessions", {
                          shown: visibleSessions.length,
                          total: sessionsQuery.data.length,
                        })}
                      </Typography>
                    ) : null}
                    <List dense disablePadding>
                    {visibleSessions.map((session) => (
                      <ListItem
                        key={session.id}
                        divider
                        disableGutters
                        secondaryAction={<Chip label={session.state ? translateAdminStatus(session.state, t) : unavailable} size="small" />}
                        sx={{ pr: 11, minWidth: 0 }}
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
                            primary: { component: "div", variant: "body2", fontWeight: 600, sx: { overflowWrap: "anywhere" } },
                            secondary: { component: "div", variant: "caption" },
                          }}
                        />
                      </ListItem>
                    ))}
                  </List>
                  </>
                ) : null}
                {sessionsQuery.isError && sessionsQuery.data ? (
                  <Alert severity="warning" sx={{ py: 0.25, "& .MuiAlert-message": { py: 0.25 } }}>
                    {t("Showing the last available data. Could not refresh.")}
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
          <Card
            component="section"
            aria-labelledby="recent-activity-title"
            elevation={0}
            sx={{ minWidth: 0, border: "1px solid", borderColor: "divider", borderRadius: "12px" }}
          >
            <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
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
                {logsQuery.isError && !logsQuery.data ? (
                  <QueryError
                    title="Unable to load recent activity"
                    error={logsQuery.error}
                    retry={() => logsQuery.refetch()}
                    t={t}
                  />
                ) : null}
                {logsQuery.isPending ? (
                  <Stack direction="row" spacing={1} alignItems="center" role="status" aria-live="polite">
                    <CircularProgress size={18} />
                    <Typography variant="body2">{t("Loading recent activity")}</Typography>
                  </Stack>
                ) : null}
                {logsQuery.data && logsQuery.data.content.length === 0 ? (
                  <Alert severity="info" sx={{ py: 0.25, "& .MuiAlert-message": { py: 0.25 } }}>
                    {t("No audit history.")}
                  </Alert>
                ) : null}
                {logsQuery.data && logsQuery.data.content.length > 0 ? (
                  <>
                    {logsQuery.data.totalElements > visibleActivity.length ? (
                      <Typography variant="caption" color="text.secondary">
                      {t("Showing {{shown}} of {{total}} entries", {
                        shown: visibleActivity.length,
                        total: logsQuery.data.totalElements,
                      })}
                      </Typography>
                    ) : null}
                    <List dense disablePadding>
                      {visibleActivity.map((entry) => (
                        <ListItem key={entry.id} divider disableGutters alignItems="flex-start">
                          <ListItemText
                            primary={entry.action ? translateAdminValue(entry.action, t) : unavailable}
                            secondary={formatTimestamp(entry.createdAt, locale, unavailable)}
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
                  <Alert severity="warning" sx={{ py: 0.25, "& .MuiAlert-message": { py: 0.25 } }}>
                    {t("Showing the last available data. Could not refresh.")}
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
      </Box>
    </Stack>
  );
}
