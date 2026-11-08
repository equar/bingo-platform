import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Alert, Box, Button, Card, CardContent, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { signupRewardGrants } from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { useListSessionState } from "../../components/useListSessionState";
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { StatusState } from "../../components/StatusState";

export function SignupRewardGrantsPage() {
  const { t } = useTranslation();
  const [query, setQuery] = usePrivateListSearch("signup-reward-grants");
  const [page, setPage] = useListSessionState("signup-reward-grants:page", 0);
  const [pageSize, setPageSize] = useListSessionState("signup-reward-grants:size", 20);
  const normalizedQuery = query.trim();
  const grantsQuery = useQuery({
    queryKey: ["signup-reward-grants", normalizedQuery, page, pageSize],
    queryFn: () => signupRewardGrants(page, pageSize, normalizedQuery || undefined),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });

  if (grantsQuery.isLoading) {
    return <StatusState title={t("Loading signup reward grants")} loading message={t("Fetching signup reward grants")} />;
  }

  if (grantsQuery.error && !grantsQuery.data) {
    return (
      <StatusState
        title={t("Unable to load signup reward grants")}
        message={t(getUserFacingErrorMessage(grantsQuery.error) ?? "Something went wrong. Please try again.")}
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
          title={t("Signup reward grants")}
          description={t("Review signup rewards awarded to registered players.")}
          actions={(
            <Button variant="outlined" onClick={() => grantsQuery.refetch()} disabled={grantsQuery.isFetching}>
              {grantsQuery.isFetching ? t("Loading") : t("Refresh")}
            </Button>
          )}
        />
        <TextField
          label={t("Search player, phone, reward, currency, or reason")}
          value={query}
          onChange={(event) => { setQuery(event.target.value); setPage(0); }}
          fullWidth
        />
        {grantsQuery.error ? (
          <Alert severity="error">{t(getUserFacingErrorMessage(grantsQuery.error) ?? "Something went wrong. Please try again.")}</Alert>
        ) : null}
        {grantsQuery.isFetching && !grantsQuery.isLoading ? (
          <Alert severity="info">{t("Updating signup reward grants...")}</Alert>
        ) : null}
        <Card elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
          <CardContent>
            <Box sx={{ overflowX: "auto" }}>
              <Table size="small" aria-label={t("Signup reward grants")}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t("Player")}</TableCell>
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
                      <TableCell>{grant.playerPhoneNumber}</TableCell>
                      <TableCell align="right">{grant.rewardAmount}</TableCell>
                      <TableCell>{grant.currency}</TableCell>
                      <TableCell>{new Date(grant.awardedAt).toLocaleString()}</TableCell>
                    </TableRow>
                  ))}
                  {grantsQuery.data && grants.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5}>
                        <Typography color="text.secondary">
                          {normalizedQuery ? t("No signup reward grants match the current search.") : t("No signup reward grants yet.")}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : null}
                </TableBody>
              </Table>
            </Box>
            <AdminTablePagination
              component="div"
              count={grantsQuery.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(event) => { setPageSize(Number(event.target.value)); setPage(0); }}
              rowsPerPageOptions={[10, 20, 50]}
            />
          </CardContent>
        </Card>
      </Stack>
    </AdminTablePage>
  );
}
