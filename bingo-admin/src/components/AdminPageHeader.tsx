import { Box, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface AdminPageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

/** Shared placement for page titles, short context, and the primary page action. */
export function AdminPageHeader({ title, description, actions }: AdminPageHeaderProps) {
  return (
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
      {actions ? <Stack direction="row" gap={1} flexWrap="wrap" justifyContent={{ xs: "flex-start", sm: "flex-end" }}>{actions}</Stack> : null}
    </Stack>
  );
}
