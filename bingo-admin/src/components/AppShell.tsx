import {
  AppBar,
  Avatar,
  Box,
  Button,
  CssBaseline,
  Divider,
  Drawer,
  Menu,
  MenuItem,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Toolbar,
  Typography,
} from "@mui/material";
import type { Theme } from "@mui/material/styles";
import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { clearSession, useSession } from "../api/session";

const drawerWidth = 260;
const idleTimeoutMs = 15 * 60 * 1000;
const activityEvents = ["click", "keydown", "mousemove", "mousedown", "scroll", "touchstart", "pointerdown"];

const navGroups = [
  {
    title: "Overview",
    items: [
      { to: "/dashboard", label: "Dashboard" },
    ],
  },
  {
    title: "Urgent work",
    items: [
      { to: "/support/tickets", label: "Support" },
      { to: "/claims", label: "Win Claims" },
      { to: "/deposits", label: "Deposits" },
    ],
  },
  {
    title: "Live operations",
    items: [
      { to: "/sessions", label: "Sessions" },
      { to: "/rooms", label: "Rooms" },
      { to: "/audit-logs", label: "Audit Logs" },
    ],
  },
  {
    title: "Account management",
    items: [
      { to: "/admin-users", label: "Admin Users" },
      { to: "/agents", label: "Agents" },
      { to: "/players", label: "Players" },
    ],
  },
  {
    title: "Policies",
    items: [
      { to: "/wallet", label: "Wallet Policies" },
      { to: "/wallet/transfers", label: "Transfer History" },
      { to: "/prize-entitlements", label: "Prize Entitlements" },
    ],
  },
];

export function AppShell() {
  const session = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const [accountMenuAnchor, setAccountMenuAnchor] = useState<null | HTMLElement>(null);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(() => new Set(navGroups.map((group) => group.title)));

  const accountMenuOpen = Boolean(accountMenuAnchor);

  const activeGroupTitles = useMemo(
    () => navGroups.filter((group) => group.items.some((item) => location.pathname === item.to || location.pathname.startsWith(`${item.to}/`))).map((group) => group.title),
    [location.pathname],
  );

  useEffect(() => {
    setExpandedGroups((currentGroups) => {
      const nextGroups = new Set(currentGroups);
      activeGroupTitles.forEach((groupTitle) => nextGroups.add(groupTitle));
      return nextGroups;
    });
  }, [activeGroupTitles]);

  useEffect(() => {
    if (!session) {
      return;
    }

    let timeoutId = window.setTimeout(handleIdleLogout, idleTimeoutMs);

    function resetIdleTimer() {
      window.clearTimeout(timeoutId);
      timeoutId = window.setTimeout(handleIdleLogout, idleTimeoutMs);
    }

    function handleIdleLogout() {
      clearSession();
      setAccountMenuAnchor(null);
      navigate("/login", { replace: true });
    }

    activityEvents.forEach((eventName) => {
      window.addEventListener(eventName, resetIdleTimer, { passive: true });
    });

    return () => {
      window.clearTimeout(timeoutId);
      activityEvents.forEach((eventName) => {
        window.removeEventListener(eventName, resetIdleTimer);
      });
    };
  }, [navigate, session]);

  const handleLogout = () => {
    clearSession();
    navigate("/login", { replace: true });
    setAccountMenuAnchor(null);
  };

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      <CssBaseline />
      <AppBar position="fixed" sx={{ zIndex: (theme: Theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            Bingo Admin
          </Typography>
          <Button
            color="inherit"
            onClick={(event) => setAccountMenuAnchor(event.currentTarget)}
            sx={{
              px: 1.5,
              py: 0.75,
              borderRadius: 999,
              border: "1px solid rgba(255,255,255,0.28)",
              textTransform: "none",
              color: "inherit",
            }}
          >
            <Stack direction="row" spacing={1} alignItems="center">
              <Avatar sx={{ width: 28, height: 28, bgcolor: "rgba(255,255,255,0.18)", fontSize: 13 }}>
                {session?.user.displayName?.slice(0, 1)?.toUpperCase() ?? "A"}
              </Avatar>
              <Stack spacing={0} alignItems="flex-start">
                <Typography variant="body2" lineHeight={1.2}>
                  {session?.user.displayName ?? "Account"}
                </Typography>
                <Typography variant="caption" sx={{ opacity: 0.8, lineHeight: 1.2 }}>
                  {session?.user.roles.join(", ") ?? "No role"}
                </Typography>
              </Stack>
            </Stack>
          </Button>
          <Menu
            anchorEl={accountMenuAnchor}
            open={accountMenuOpen}
            onClose={() => setAccountMenuAnchor(null)}
            anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
            transformOrigin={{ vertical: "top", horizontal: "right" }}
          >
            <MenuItem
              onClick={() => {
                navigate("/profile");
                setAccountMenuAnchor(null);
              }}
            >
              Profile
            </MenuItem>
            <MenuItem
              onClick={() => {
                navigate("/change-password");
                setAccountMenuAnchor(null);
              }}
            >
              Change password
            </MenuItem>
            <MenuItem onClick={handleLogout}>Logout</MenuItem>
          </Menu>
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
          {navGroups.map((group, groupIndex) => (
            <Box key={group.title} sx={{ mb: groupIndex === navGroups.length - 1 ? 0 : 2 }}>
              <ListItemButton
                onClick={() => {
                  setExpandedGroups((currentGroups) => {
                    const nextGroups = new Set(currentGroups);
                    if (nextGroups.has(group.title)) {
                      nextGroups.delete(group.title);
                    } else {
                      nextGroups.add(group.title);
                    }
                    return nextGroups;
                  });
                }}
                sx={{ borderRadius: 2, px: 1.5, py: 0.5 }}
                aria-expanded={expandedGroups.has(group.title)}
                aria-controls={`nav-group-${group.title.toLowerCase().replace(/\s+/g, "-")}`}
              >
                <ListItemText
                  primary={group.title}
                  primaryTypographyProps={{ variant: "overline", color: "text.secondary" }}
                />
                <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                  {expandedGroups.has(group.title) ? "▾" : "▸"}
                </Typography>
              </ListItemButton>
              {expandedGroups.has(group.title) ? (
                <List id={`nav-group-${group.title.toLowerCase().replace(/\s+/g, "-")}`}>
                  {group.items.map((item) => (
                    <ListItemButton
                      key={item.to}
                      component={NavLink}
                      to={item.to}
                      selected={location.pathname === item.to || location.pathname.startsWith(`${item.to}/`)}
                      sx={{ borderRadius: 2, mb: 0.5, ml: 1 }}
                    >
                      <ListItemText primary={item.label} />
                    </ListItemButton>
                  ))}
                </List>
              ) : null}
              {groupIndex < navGroups.length - 1 ? <Divider sx={{ my: 2 }} /> : null}
            </Box>
          ))}
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
