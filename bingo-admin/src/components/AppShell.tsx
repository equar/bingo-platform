import {
  AppBar,
  Box,
  CssBaseline,
  Divider,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Toolbar,
  Typography,
} from "@mui/material";
import type { Theme } from "@mui/material/styles";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useSession } from "../api/session";

const drawerWidth = 260;

const navItems = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/rooms", label: "Rooms" },
  { to: "/sessions", label: "Sessions" },
  { to: "/players", label: "Players" },
  { to: "/agents", label: "Agents" },
  { to: "/support/tickets", label: "Support" },
  { to: "/claims", label: "Win Claims" },
  { to: "/deposits", label: "Deposits" },
  { to: "/audit-logs", label: "Audit Logs" },
];

export function AppShell() {
  const session = useSession();
  const location = useLocation();

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      <CssBaseline />
      <AppBar position="fixed" sx={{ zIndex: (theme: Theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            Bingo Admin
          </Typography>
          <Stack spacing={0.25} alignItems="flex-end">
            <Typography variant="body2">{session?.user.displayName ?? "Signed out"}</Typography>
            <Typography variant="caption" sx={{ opacity: 0.8 }}>
              {session?.user.roles.join(", ") ?? "No role"}
            </Typography>
          </Stack>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: "border-box" },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: "auto", p: 1 }}>
          <List>
            {navItems.map((item) => (
              <ListItemButton
                key={item.to}
                component={NavLink}
                to={item.to}
                selected={location.pathname === item.to || location.pathname.startsWith(`${item.to}/`)}
                sx={{ borderRadius: 2, mb: 0.5 }}
              >
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
          <Divider sx={{ my: 2 }} />
          <Typography variant="caption" color="text.secondary">
            Backend authorization remains authoritative.
          </Typography>
        </Box>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
