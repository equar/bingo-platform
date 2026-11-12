import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useLocation, useSearchParams } from "react-router-dom";
import {
  AdminAgentSummaryResponse,
  approveAgent,
  adminAgents,
  commissionRule,
  currentCommissionRule,
  suspendAgent,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus } from "../../i18n";
import { FloatFundingPanel } from "./FloatFundingPanel";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";

function formatNullable(value: string | null | undefined) {
  return value?.trim() ? value : "n/a";
}

export function AgentsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const commissionRuleQuery = useQuery({ queryKey: ["commission-rule"], queryFn: currentCommissionRule });
  const [search, setSearch] = usePrivateListSearch("agents");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const statusValue = searchParams.get("status") ?? "";
  const statusFilter = ["PENDING", "APPROVED", "SUSPENDED"].includes(statusValue) ? statusValue : "";
  const pageValue = Number(searchParams.get("page") ?? 0);
  const page = Number.isSafeInteger(pageValue) ? Math.max(0, pageValue) : 0;
  const sizeValue = Number(searchParams.get("size") ?? 20);
  const pageSize = [10, 20, 50, 100].includes(sizeValue) ? sizeValue : 20;
  const updateListState = useCallback((updates: Record<string, string>) => setSearchParams((current) => {
    const next = new URLSearchParams(current);
    Object.entries(updates).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    return next;
  }, { replace: true }), [setSearchParams]);
  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timeout);
  }, [search]);
  const agentsQuery = useQuery({
    queryKey: ["agents", page, pageSize, debouncedSearch, statusFilter],
    queryFn: () => adminAgents(page, pageSize, debouncedSearch, statusFilter),
    placeholderData: (previous) => previous,
  });
  const [selectedAgent, setSelectedAgent] = useState<AdminAgentSummaryResponse | null>(null);
  const [decisionDraft, setDecisionDraft] = useState<{ mode: "APPROVE" } | null>(null);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [suspendConfirmOpen, setSuspendConfirmOpen] = useState(false);
  const [commissionRateBps, setCommissionRateBps] = useState("0");
  const [commissionEffectiveFrom, setCommissionEffectiveFrom] = useState(new Date().toISOString().slice(0, 16));
  const [commissionActive, setCommissionActive] = useState(true);
  useEffect(() => {
    const current = commissionRuleQuery.data;
    if (!current || current.versionNumber === 0) return;
    setCommissionRateBps(String(current.commissionRateBps));
    setCommissionEffectiveFrom(current.effectiveFrom.slice(0, 16));
    setCommissionActive(current.active);
  }, [commissionRuleQuery.data]);

  const approveMutation = useMutation({
    mutationFn: async () => {
      if (!selectedAgent) {
        throw new Error("Missing selected agent");
      }
      return approveAgent(selectedAgent.agentId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setDecisionDraft(null);
      setSelectedAgent(null);
      setDecisionError(null);
    },
    onError: (error) => setDecisionError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const suspendMutation = useMutation({
    mutationFn: async () => {
      if (!selectedAgent) {
        throw new Error("Missing selected agent");
      }
      return suspendAgent(selectedAgent.agentId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agents"] });
      setSuspendConfirmOpen(false);
      setSelectedAgent(null);
      setDecisionError(null);
    },
    onError: (error) => setDecisionError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const commissionMutation = useMutation({
    mutationFn: () =>
      commissionRule({
        commissionRateBps: Number(commissionRateBps),
        effectiveFrom: new Date(commissionEffectiveFrom).toISOString(),
        active: commissionActive,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["commission-rule"] });
    },
  });

  const agents = agentsQuery.data?.content ?? [];
  useEffect(() => {
    const data = agentsQuery.data;
    if (!data || agentsQuery.isPlaceholderData) return;
    const lastAvailablePage = Math.max(0, data.totalPages - 1);
    if (page > lastAvailablePage) updateListState({ page: lastAvailablePage ? String(lastAvailablePage) : "" });
  }, [agentsQuery.data, agentsQuery.isPlaceholderData, page, updateListState]);

  if (agentsQuery.isLoading) {
    return <StatusState title={t("Loading agents")} loading message={t("Fetching agent roster")} />;
  }

  if (agentsQuery.error && !agentsQuery.data) {
    return (
      <StatusState
        title={t("Unable to load agents")}
        message={t(getUserFacingErrorMessage(agentsQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => agentsQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <AdminPageHeader title={t("Agents")} description={t("Review agent profiles, queue float top-up requests, and publish commission rules.")} refresh={{ isFetching: agentsQuery.isFetching, failed: agentsQuery.isRefetchError, error: agentsQuery.error, onRefresh: () => agentsQuery.refetch() }} />

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">{t("Commission rules")}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t("Publish a versioned rule. The current rule loads again after refresh.")}</Typography>
                {commissionRuleQuery.data && commissionRuleQuery.data.versionNumber > 0 ? (
                  <Typography variant="body2" data-testid="current-commission-rule">
                    {t("Current version")}{" "}{commissionRuleQuery.data.versionNumber} {t("·")}{" "}{t(commissionRuleQuery.data.active ? "Active" : "Inactive")}
                  </Typography>
                ) : null}
                {commissionMutation.isError ? <Alert severity="error" role="alert">{t(getUserFacingErrorMessage(commissionMutation.error) ?? "Something went wrong. Please try again.")}</Alert> : null}
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label={t("Commission rate (bps)")}
                      type="number"
                      value={commissionRateBps}
                      onChange={(event) => setCommissionRateBps(event.target.value)}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label={t("Effective from")}
                      type="datetime-local"
                      value={commissionEffectiveFrom}
                      onChange={(event) => setCommissionEffectiveFrom(event.target.value)}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={commissionActive}
                          onChange={(event) => setCommissionActive(event.target.checked)}
                        />
                      }
                      label={t("Active")}
                    />
                  </Grid>
                </Grid>
                <Button
                  variant="contained"
                  onClick={() => commissionMutation.mutate()}
                  disabled={commissionMutation.isPending}
                >
                  {t("Publish rule")}</Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <FloatFundingPanel />

      <TextField
        label={t("Search agents")}
        value={search}
        onChange={(event) => { setSearch(event.target.value); updateListState({ page: "" }); }}
        fullWidth
        helperText={t("Search by name, phone, city, territory, or status")}
      />
      <TextField
        select
        label={t("Status")}
        value={statusFilter}
        onChange={(event) => updateListState({ status: event.target.value, page: "" })}
        sx={{ maxWidth: 240 }}
      >
        <MenuItem value="">{t("All statuses")}</MenuItem>
        <MenuItem value="PENDING">{t("Pending")}</MenuItem>
        <MenuItem value="APPROVED">{t("Approved")}</MenuItem>
        <MenuItem value="SUSPENDED">{t("Suspended")}</MenuItem>
      </TextField>

      <Card sx={{ border: "1px solid", borderColor: "divider" }}>
        <CardContent sx={{ p: 0 }}>
          <Table size="small" aria-label={t("agents table")}>
            <TableHead>
              <TableRow>
                <TableCell>{t("Agent")}</TableCell>
                <TableCell>{t("City")}</TableCell>
                <TableCell>{t("Territory")}</TableCell>
                <TableCell>{t("Status")}</TableCell>
                <TableCell>{t("Float")}</TableCell>
                <TableCell align="right">{t("Tickets")}</TableCell>
                <TableCell align="right">{t("Float tx")}</TableCell>
                <TableCell align="right">{t("Top-ups")}</TableCell>
                <TableCell align="right">{t("Payouts")}</TableCell>
                <TableCell>{t("Actions")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {agents.map((agent) => (
                <TableRow key={agent.agentId} hover>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Typography fontWeight={600}>{agent.displayName}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {agent.normalizedPhone}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{formatNullable(agent.city)}</TableCell>
                  <TableCell>{formatNullable(agent.territory)}</TableCell>
                  <TableCell>
                    <Chip label={translateAdminStatus(agent.status, t)} size="small" />
                  </TableCell>
                  <TableCell><Chip label={t("See top-up queue")} variant="outlined" size="small" /></TableCell>
                  <TableCell align="right">{agent.ticketSalesCount}</TableCell>
                  <TableCell align="right">{agent.floatTransactionCount}</TableCell>
                  <TableCell align="right">{agent.topUpRequestCount}</TableCell>
                  <TableCell align="right">{agent.payoutVerificationCount}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Button size="small" component={RouterLink} to={`/agents/${agent.agentId}${location.search}`}>
                        {t("Detail")}</Button>
                      <Button
                        size="small"
                        variant="outlined"
                        disabled={agent.status !== "PENDING"}
                        onClick={() => {
                          setSelectedAgent(agent);
                          setDecisionError(null);
                          setDecisionDraft({ mode: "APPROVE" });
                        }}
                      >
                        {t("Approve")}</Button>
                      <Button
                        size="small"
                        color="warning"
                        variant="outlined"
                        disabled={agent.status !== "APPROVED"}
                        onClick={() => {
                          setSelectedAgent(agent);
                          setDecisionError(null);
                          setSuspendConfirmOpen(true);
                        }}
                      >
                        {t("Suspend")}</Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
              {agents.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10}>
                    <Alert severity="info">{t("No agents match the current search and filters.")}</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      {agentsQuery.data ? (
        <AdminTablePagination
          component="div"
          dataLoaded={!!agentsQuery.data}
          count={agentsQuery.data.totalElements}
          page={page}
          onPageChange={(_, nextPage) => updateListState({ page: String(nextPage) })}
          rowsPerPage={pageSize}
          onRowsPerPageChange={(event) => updateListState({ size: event.target.value, page: "" })}
          rowsPerPageOptions={[10, 20, 50, 100]}
        />
      ) : null}

      <Dialog open={Boolean(decisionDraft)} onClose={() => setDecisionDraft(null)} fullWidth maxWidth="sm">
        <DialogTitle>{t("Approve agent application")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {decisionError ? <Alert severity="error" role="alert">{t(decisionError)}</Alert> : null}
            <Typography>
              {selectedAgent
                ? t("Approve {{name}} ({{phone}})?", {
                    name: selectedAgent.displayName,
                    phone: selectedAgent.normalizedPhone,
                  })
                : t("Review the agent application before approving.")}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t("Review the submitted identity details on the agent detail page. Approval activates the agent account; the API ignores client supplied status fields.")}</Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecisionDraft(null)} disabled={approveMutation.isPending}>{t("Cancel")}</Button>
          <Button variant="contained" onClick={() => approveMutation.mutate()} disabled={approveMutation.isPending || selectedAgent?.status !== "PENDING"}>
            {t("Approve")}</Button>
        </DialogActions>
      </Dialog>
      <ConfirmDialog
        open={suspendConfirmOpen}
        title={t("Suspend agent")}
        description={
          selectedAgent
            ? t("Suspend {{name}}? The backend endpoint does not accept a suspension reason.", { name: selectedAgent.displayName })
            : t("Suspend selected agent?")
        }
        confirmLabel={t("Suspend")}
        cancelLabel={t("Back")}
        confirming={suspendMutation.isPending}
        error={decisionError ? t(decisionError) : undefined}
        onCancel={() => { setSuspendConfirmOpen(false); setDecisionError(null); }}
        onConfirm={() => suspendMutation.mutate()}
      />
    </Stack>
  );
}
