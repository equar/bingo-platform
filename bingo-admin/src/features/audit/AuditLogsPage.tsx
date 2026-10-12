import { useQuery } from "@tanstack/react-query";
import { Alert, Box, Card, CardContent, Chip, Divider, Stack, Typography } from "@mui/material";
import { auditLogs } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

export function AuditLogsPage() {
  const auditQuery = useQuery({ queryKey: ["audit-logs"], queryFn: () => auditLogs(0, 100) });

  if (auditQuery.isLoading) {
    return <StatusState title="Loading audit logs" loading message="Fetching recent audit history" />;
  }

  if (auditQuery.error) {
    return (
      <StatusState
        title="Unable to load audit logs"
        message={(auditQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => auditQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Audit logs</Typography>
        <Typography color="text.secondary">Recent system activity and review actions.</Typography>
      </Box>

      {(auditQuery.data?.content ?? []).length === 0 ? <Alert severity="info">No audit entries found.</Alert> : null}

      <Stack spacing={1.5}>
        {(auditQuery.data?.content ?? []).map((entry) => (
          <Card key={entry.id} sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={1}>
                <Box display="flex" justifyContent="space-between" gap={2} alignItems="center">
                  <Typography variant="h6">{entry.action}</Typography>
                  <Chip label={entry.operationId ?? "n/a"} size="small" />
                </Box>
                <Typography variant="body2">
                  Actor {entry.actorUserId ?? "system"} · {entry.targetType} {entry.targetId ?? "n/a"}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {entry.reason ?? "No reason provided"}
                </Typography>
                <Divider />
                <Typography variant="caption" color="text.secondary">
                  {new Date(entry.createdAt).toLocaleString()}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>
    </Stack>
  );
}
