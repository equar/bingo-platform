import { Alert, Box, Button, CircularProgress, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface StatusStateProps {
  title: string;
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: ReactNode;
  loading?: boolean;
}

export function StatusState({ title, message, actionLabel, onAction, icon, loading }: StatusStateProps) {
  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{
        borderRadius: 3,
        border: "1px solid",
        borderColor: "divider",
        p: { xs: 2.5, sm: 4 },
        minHeight: loading ? 178 : 188,
        display: "grid",
        placeItems: "center",
        backgroundColor: "background.paper",
        boxShadow: "0 1px 3px rgba(23,36,59,0.025)",
      }}
    >
      <Stack spacing={2} alignItems="center" textAlign="center">
        {loading ? <CircularProgress size={28} /> : icon}
        <Typography component="h2" variant="h6">{title}</Typography>
        {message ? (
          <Alert severity="info" sx={{ width: "100%" }}>
            {message}
          </Alert>
        ) : null}
        {onAction && actionLabel ? (
          <Button variant="outlined" onClick={onAction}>
            {actionLabel}
          </Button>
        ) : null}
      </Stack>
    </Box>
  );
}
