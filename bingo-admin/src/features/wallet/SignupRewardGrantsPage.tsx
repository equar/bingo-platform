import { formatEthiopianDateTime } from "../../utils/ethiopianDateTime";
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import {
  awardPromotionalReward,
  promotionalRewardCampaigns,
  promotionalRewardGrants,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { useListSessionState } from "../../components/useListSessionState";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { StatusState } from "../../components/StatusState";

export function SignupRewardGrantsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [campaignId, setCampaignId] = useState("");
  const [playerId, setPlayerId] = useState("");
  const [query, setQuery] = usePrivateListSearch("signup-reward-grants");
  const [page, setPage] = useListSessionState("signup-reward-grants:page", 0);
  const [pageSize, setPageSize] = useListSessionState(
    "signup-reward-grants:size",
    20,
  );
  const normalizedQuery = query.trim();
  const grantsQuery = useQuery({
    queryKey: ["promotional-reward-grants", normalizedQuery, page, pageSize],
    queryFn: () =>
      promotionalRewardGrants(page, pageSize, normalizedQuery || undefined),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
  const campaignsQuery = useQuery({
    queryKey: ["promotional-reward-campaigns"],
    queryFn: () => promotionalRewardCampaigns(0, 100),
  });
  const awardMutation = useMutation({
    mutationFn: () =>
      awardPromotionalReward(Number(campaignId), Number(playerId)),
    onSuccess: async () => {
      setPlayerId("");
      await queryClient.invalidateQueries({
        queryKey: ["promotional-reward-grants"],
      });
    },
  });

  if (grantsQuery.isLoading) {
    return (
      <StatusState
        title={t("Loading signup reward grants")}
        loading
        message={t("Fetching signup reward grants")}
      />
    );
  }

  if (grantsQuery.error && !grantsQuery.data) {
    return (
      <StatusState
        title={t("Unable to load signup reward grants")}
        message={t(
          getUserFacingErrorMessage(grantsQuery.error) ??
            "Something went wrong. Please try again.",
        )}
        actionLabel={t("Retry")}
        onAction={() => grantsQuery.refetch()}
      />
    );
  }

  const grants = grantsQuery.data?.content ?? [];

  return (
    <AdminTablePage>
      <Stack spacing={3}>
        <AdminPageHeader
          title={t("Promotional reward grants")}
          description={t(
            "Review wallet bonus awards and issue manual campaign grants to players.",
          )}
          refresh={{
            isFetching: grantsQuery.isFetching || campaignsQuery.isFetching,
            failed: grantsQuery.isRefetchError || campaignsQuery.isRefetchError,
            error: grantsQuery.error ?? campaignsQuery.error,
            onRefresh: () =>
              Promise.all([grantsQuery.refetch(), campaignsQuery.refetch()]),
          }}
        />
        <Card
          elevation={0}
          sx={{ border: "1px solid", borderColor: "divider" }}
        >
          <CardContent>
            <Typography variant="h6" gutterBottom>
              {t("Award a manual campaign")}
            </Typography>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                select
                label={t("Manual campaign")}
                value={campaignId}
                onChange={(event) => setCampaignId(event.target.value)}
                fullWidth
              >
                {(campaignsQuery.data?.content ?? [])
                  .filter(
                    (campaign) =>
                      campaign.triggerType === "MANUAL" && campaign.activeNow,
                  )
                  .map((campaign) => (
                    <MenuItem key={campaign.id} value={String(campaign.id)}>
                      {campaign.name} · {campaign.rewardAmount}{" "}
                      {campaign.currency}
                    </MenuItem>
                  ))}
              </TextField>
              <TextField
                label={t("Player ID")}
                type="number"
                value={playerId}
                onChange={(event) => setPlayerId(event.target.value)}
                inputProps={{ min: 1 }}
                fullWidth
              />
              <Button
                variant="contained"
                onClick={() => awardMutation.mutate()}
                disabled={
                  !campaignId ||
                  !playerId ||
                  Number(playerId) <= 0 ||
                  awardMutation.isPending
                }
              >
                {awardMutation.isPending ? t("Awarding") : t("Award bonus")}
              </Button>
            </Stack>
            {campaignsQuery.error && !campaignsQuery.data ? (
              <Alert severity="error" sx={{ mt: 2 }}>
                {t(
                  getUserFacingErrorMessage(campaignsQuery.error) ??
                    "Something went wrong. Please try again.",
                )}
              </Alert>
            ) : null}
            {awardMutation.error ? (
              <Alert severity="error" sx={{ mt: 2 }}>
                {t(
                  getUserFacingErrorMessage(awardMutation.error) ??
                    "Something went wrong. Please try again.",
                )}
              </Alert>
            ) : null}
            {awardMutation.isSuccess ? (
              <Alert severity="success" sx={{ mt: 2 }}>
                {t("Promotional balance awarded successfully.")}
              </Alert>
            ) : null}
          </CardContent>
        </Card>
        <TextField
          label={t("Search player, phone, campaign, reward, or currency")}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          fullWidth
        />
        {grantsQuery.isFetching && !grantsQuery.isLoading ? (
          <Alert severity="info">
            {t("Updating promotional reward grants...")}
          </Alert>
        ) : null}
        <Card
          elevation={0}
          sx={{ border: "1px solid", borderColor: "divider" }}
        >
          <CardContent>
            <Box sx={{ overflowX: "auto" }}>
              <Table size="small" aria-label={t("Promotional reward grants")}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t("Player")}</TableCell>
                    <TableCell>{t("Campaign")}</TableCell>
                    <TableCell>{t("Phone number")}</TableCell>
                    <TableCell align="right">{t("Reward amount")}</TableCell>
                    <TableCell>{t("Currency")}</TableCell>
                    <TableCell>{t("Awarded")}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {grants.map((grant) => (
                    <TableRow key={grant.id} hover>
                      <TableCell>{grant.playerDisplayName}</TableCell>
                      <TableCell>{grant.campaignName}</TableCell>
                      <TableCell>{grant.playerPhoneNumber}</TableCell>
                      <TableCell align="right">{grant.rewardAmount}</TableCell>
                      <TableCell>{grant.currency}</TableCell>
                      <TableCell>
                        {formatEthiopianDateTime(grant.awardedAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                  {grantsQuery.data && grants.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6}>
                        <Typography color="text.secondary">
                          {normalizedQuery
                            ? t(
                                "No promotional reward grants match the current search.",
                              )
                            : t("No promotional reward grants yet.")}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : null}
                </TableBody>
              </Table>
            </Box>
            <AdminTablePagination
              component="div"
              dataLoaded={!!grantsQuery.data}
              count={grantsQuery.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(event) => {
                setPageSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 20, 50]}
            />
          </CardContent>
        </Card>
      </Stack>
    </AdminTablePage>
  );
}
