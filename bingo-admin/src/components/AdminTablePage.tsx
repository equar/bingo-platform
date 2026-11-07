import type { ReactNode } from "react";
import { Box } from "@mui/material";

/** Gives a table-focused route the space below the fixed app bar while keeping its controls and pagination in view. */
export function AdminTablePage({ children, enabled = true }: { children: ReactNode; enabled?: boolean }) {
  return (
    <Box className={enabled ? "admin-table-page" : undefined} sx={{ minWidth: 0 }}>
      {children}
    </Box>
  );
}
