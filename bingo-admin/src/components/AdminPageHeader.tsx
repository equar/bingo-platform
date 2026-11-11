import { Box, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";
import { ListRefreshButton, ListRefreshError } from "./ListRefreshButton";

export interface AdminPageRefresh {
  isFetching: boolean;
  onRefresh: () => unknown;
  error?: unknown;
  failed?: boolean;
}

interface AdminPageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  refresh?: AdminPageRefresh;
}

/** Shared placement for page titles, short context, and the primary page action. */
export function AdminPageHeader({ title, description, actions, refresh }: AdminPageHeaderProps) {
  return (
    <Stack spacing={1} sx={{ minWidth: 0 }}>
      <Stack
        component="header"
        direction={{ xs: "column", sm: "row" }}
        alignItems={{ xs: "stretch", sm: "center" }}
        justifyContent="space-between"
        gap={1.5}
        sx={{ minWidth: 0 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h4" component="h1">{title}</Typography>
          {description ? <Typography color="text.secondary">{description}</Typography> : null}
        </Box>
        {actions || refresh ? <Stack direction="row" gap={1} flexWrap="wrap" justifyContent={{ xs: "flex-start", sm: "flex-end" }}>
          {actions}
          {refresh ? <ListRefreshButton refreshing={refresh.isFetching} onRefresh={refresh.onRefresh} /> : null}
        </Stack> : null}
      </Stack>
      {refresh?.failed ? (
        <ListRefreshError retrying={refresh.isFetching} error={refresh.error} onRetry={refresh.onRefresh} />
      ) : null}
    </Stack>
  );
}
