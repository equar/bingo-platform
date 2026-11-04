import {
  AppBar,
  Avatar,
  Box,
  Button,
  CssBaseline,
  Divider,
  Drawer,
  IconButton,
  Menu,
  MenuItem,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
} from "@mui/material";
import type { Theme } from "@mui/material/styles";
import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { clearSession, useSession } from "../api/session";
import { useTranslation } from "react-i18next";
import { translateAdminRole } from "../i18n";

const expandedDrawerWidth = 248;
const collapsedDrawerWidth = 72;
const idleTimeoutMs = 15 * 60 * 1000;
const activityEvents = ["click", "keydown", "mousemove", "mousedown", "scroll", "touchstart", "pointerdown"];

const navGroups = [
  {
    title: "nav.overview",
    standalone: true,
    items: [
      { to: "/dashboard", label: "nav.dashboard", icon: "dashboard" },
    ],
  },
  {
    title: "nav.operations",
    items: [
      { to: "/sessions", label: "nav.sessions", icon: "sessions" },
      { to: "/rooms", label: "nav.rooms", icon: "rooms" },
      { to: "/community", label: "nav.community", icon: "community" },
    ],
  },
  {
    title: "nav.reviews",
    items: [
      { to: "/deposits", label: "nav.deposits", icon: "deposits" },
      { to: "/claims", label: "nav.claims", icon: "claims" },
      { to: "/support/tickets", label: "nav.support", icon: "support" },
      { to: "/prize-entitlements", label: "nav.entitlements", icon: "entitlements" },
    ],
  },
  {
    title: "nav.accounts",
    items: [
      { to: "/players", label: "nav.players", icon: "players" },
      { to: "/player-verifications", label: "nav.verification", icon: "verification" },
      { to: "/agents", label: "nav.agents", icon: "agents" },
    ],
  },
  {
    title: "nav.finance",
    items: [
      { to: "/wallet/signup-reward", label: "nav.walletPolicies", icon: "wallet" },
      { to: "/wallet/transfers", label: "nav.transfers", icon: "transfers" },
    ],
  },
  {
    title: "nav.administration",
    items: [
      { to: "/admin-users", label: "nav.adminUsers", icon: "adminUsers" },
      { to: "/audit-logs", label: "nav.audit", icon: "audit" },
      { to: "/reports", label: "nav.reports", icon: "audit" },
    ],
  },
] as const;

type NavIconName =
  | "dashboard"
  | "sessions"
  | "rooms"
  | "community"
  | "deposits"
  | "claims"
  | "support"
  | "entitlements"
  | "players"
  | "verification"
  | "agents"
  | "wallet"
  | "transfers"
  | "adminUsers"
  | "audit"
  | "collapse"
  | "expand"
  | "close";

const navIconPaths: Record<NavIconName, string[]> = {
  dashboard: ["M3 3h8v8H3z", "M13 3h8v5h-8z", "M13 10h8v11h-8z", "M3 13h8v8H3z"],
  sessions: ["M3 5h18", "M5 3v4", "M19 3v4", "M5 9h14v11H5z", "M8 13h3", "M8 16h6"],
  rooms: ["M3 21V8l9-5 9 5v13", "M9 21v-7h6v7", "M7 10h.01", "M17 10h.01"],
  community: ["M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8", "M20 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75"],
  deposits: ["M12 3v12", "m7 10 5 5 5-5", "M5 17v4h14v-4"],
  claims: ["M4 4h16v16H4z", "m8 12 3 3 5-6", "M8 8h.01", "M16 8h.01"],
  support: ["M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8z"],
  entitlements: ["M12 3v18", "M17 7H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"],
  players: ["M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8", "M20 8v6", "M17 11h6"],
  verification: ["M12 3 4 6v5c0 5 3.4 8.8 8 10 4.6-1.2 8-5 8-10V6z", "m9 12 2 2 4-4"],
  agents: ["M12 2 4 6v6c0 5 3.4 8.8 8 10 4.6-1.2 8-5 8-10V6z", "m9 12 2 2 4-4"],
  wallet: ["M4 6h16v14H4z", "M4 6V4h13", "M16 12h4", "M16 12a2 2 0 1 0 0 4h4v-4z"],
  transfers: ["M7 7h14", "m17 3 4 4-4 4", "M17 17H3", "m7 13-4 4 4 4"],
  adminUsers: ["M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8", "M19 8v6", "M16 11h6"],
  audit: ["M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01"],
  collapse: ["m15 18-6-6 6-6"],
  expand: ["m9 18 6-6-6-6"],
  close: ["m18 6-12 12", "m6 6 12 12"],
};

function NavIcon({ name }: { name: NavIconName }) {
  return (
    <Box component="svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false" sx={{ width: 20, height: 20, flexShrink: 0 }}>
      {navIconPaths[name].map((path) => (
        <Box
          component="path"
          key={path}
          d={path}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ))}
    </Box>
  );
}

function isNavItemActive(pathname: string, itemPath: string) {
  if (itemPath === "/wallet/signup-reward") {
    return ["/wallet", "/wallet/signup-reward", "/wallet/transfer-policy"].includes(pathname);
  }
  return pathname === itemPath || pathname.startsWith(`${itemPath}/`);
}

export function AppShell() {
  const { t } = useTranslation();
  const session = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const [accountMenuAnchor, setAccountMenuAnchor] = useState<null | HTMLElement>(null);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const isMobile = useMediaQuery("(max-width:899.95px)");
  const [desktopNavCollapsed, setDesktopNavCollapsed] = useState(() => {
    try {
      return window.localStorage.getItem("bingo-admin-nav-collapsed") === "true";
    } catch (error) {
      console.warn("Could not read the saved navigation preference.", error);
      return false;
    }
  });
  const navigationCollapsed = !isMobile && desktopNavCollapsed;
  const drawerWidth = navigationCollapsed ? collapsedDrawerWidth : expandedDrawerWidth;
  const supportOnly = Boolean(session?.user.roles.includes("SUPPORT") && !session.user.roles.includes("ADMIN"));
  const visibleNavGroups = useMemo(
    () => supportOnly
      ? [{
          title: "nav.reviews",
          items: [
            { to: "/support/tickets", label: "nav.support", icon: "support" as const },
            { to: "/reports", label: "nav.reports", icon: "audit" as const },
          ],
        }]
      : navGroups,
    [supportOnly],
  );
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(() => new Set(
    visibleNavGroups
      .filter((group) => group.items.some((item) => isNavItemActive(location.pathname, item.to)))
      .map((group) => group.title),
  ));

  const accountMenuOpen = Boolean(accountMenuAnchor);

  const activeGroupTitles = useMemo(
    () => visibleNavGroups.filter((group) => group.items.some((item) => isNavItemActive(location.pathname, item.to))).map((group) => group.title),
    [location.pathname, visibleNavGroups],
  );

  useEffect(() => {
    setExpandedGroups(new Set(activeGroupTitles));
  }, [activeGroupTitles]);

  useEffect(() => {
    try {
      window.localStorage.setItem("bingo-admin-nav-collapsed", String(desktopNavCollapsed));
    } catch (error) {
      console.warn("Could not save the navigation preference.", error);
    }
  }, [desktopNavCollapsed]);

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
        <Toolbar sx={{ gap: 1 }}>
          {isMobile ? (
            <Button
              color="inherit"
              aria-label={t("menu.openNavigation")}
              aria-expanded={mobileNavOpen}
              aria-controls="admin-primary-navigation"
              onClick={() => setMobileNavOpen(true)}
              sx={{ minWidth: 0, px: 1, textTransform: "none" }}
            >
              {t("menu.open")}
            </Button>
          ) : null}
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            {t("brand")}
          </Typography>
          <Button
            color="inherit"
            onClick={(event) => setAccountMenuAnchor(event.currentTarget)}
            aria-haspopup="menu"
            aria-expanded={accountMenuOpen}
            aria-controls={accountMenuOpen ? "admin-account-menu" : undefined}
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
                  {session?.user.displayName ?? t("account")}
                </Typography>
                <Typography variant="caption" sx={{ opacity: 0.8, lineHeight: 1.2 }}>
                  {session?.user.roles.map((role) => translateAdminRole(role, t)).join(", ") ?? t("noRole")}
                </Typography>
              </Stack>
            </Stack>
          </Button>
          <Menu
            id="admin-account-menu"
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
              {t("menu.profile")}
            </MenuItem>
            <MenuItem
              onClick={() => {
                navigate("/change-password");
                setAccountMenuAnchor(null);
              }}
            >
              {t("menu.changePassword")}
            </MenuItem>
            <MenuItem onClick={handleLogout}>{t("menu.logout")}</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <Drawer
        variant={isMobile ? "temporary" : "permanent"}
        open={!isMobile || mobileNavOpen}
        onClose={() => setMobileNavOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: {
            width: drawerWidth,
            boxSizing: "border-box",
            overflowX: "hidden",
            transition: (theme: Theme) => theme.transitions.create("width"),
          },
        }}
      >
        <Toolbar />
        <Box
          component="nav"
          id="admin-primary-navigation"
          aria-label={t("navigation.primary")}
          sx={{ display: "flex", flex: 1, minHeight: 0, flexDirection: "column", overflowY: "auto", px: 1, pb: 1 }}
        >
          <Stack
            direction={navigationCollapsed ? "column" : "row"}
            alignItems="center"
            justifyContent={navigationCollapsed ? "center" : "space-between"}
            sx={{ px: navigationCollapsed ? 0 : 1, py: 0.5 }}
          >
            {!navigationCollapsed ? (
              <Typography variant="overline" color="text.secondary" noWrap sx={{ fontWeight: 700 }}>
                {t("navigation.primary")}
              </Typography>
            ) : null}
            {isMobile ? (
              <IconButton
                aria-label={t("menu.closeNavigation")}
                onClick={() => setMobileNavOpen(false)}
                sx={{ alignSelf: "flex-end" }}
              >
                <NavIcon name="close" />
              </IconButton>
            ) : (
              <Tooltip title={t(navigationCollapsed ? "menu.expandNavigation" : "menu.collapseNavigation")} placement="right">
                <IconButton
                  aria-label={t(navigationCollapsed ? "menu.expandNavigation" : "menu.collapseNavigation")}
                  aria-pressed={navigationCollapsed}
                  onClick={() => setDesktopNavCollapsed((collapsed) => !collapsed)}
                  size="small"
                >
                  <NavIcon name={navigationCollapsed ? "expand" : "collapse"} />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
          <Box sx={{ flex: 1 }}>
            {visibleNavGroups.map((group, groupIndex) => {
              const isExpanded = expandedGroups.has(group.title);
              const activeGroup = activeGroupTitles.includes(group.title);
              const groupId = `nav-group-${group.title.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}`;
              const groupIcon = group.items[0]?.icon ?? "dashboard";
              if ("standalone" in group && group.standalone) {
                const item = group.items[0];
                const active = isNavItemActive(location.pathname, item.to);
                return (
                  <Tooltip key={group.title} title={navigationCollapsed ? t(item.label) : ""} placement="right">
                    <ListItemButton
                      component={NavLink}
                      to={item.to}
                      onClick={() => setMobileNavOpen(false)}
                      selected={active}
                      aria-current={active ? "page" : undefined}
                      sx={{
                        borderRadius: "10px",
                        minHeight: 42,
                        mb: 0.125,
                        px: navigationCollapsed ? 1.5 : 1,
                        justifyContent: navigationCollapsed ? "center" : "flex-start",
                        "&.Mui-selected": {
                          color: "primary.main",
                          bgcolor: "action.selected",
                          "&::before": {
                            content: '""',
                            position: "absolute",
                            left: 0,
                            top: 8,
                            bottom: 8,
                            width: 3,
                            borderRadius: 3,
                            bgcolor: "primary.main",
                          },
                        },
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: navigationCollapsed ? 0 : 34, color: "inherit", justifyContent: "center" }}>
                        <NavIcon name={item.icon} />
                      </ListItemIcon>
                      {!navigationCollapsed ? <ListItemText primary={t(item.label)} /> : null}
                    </ListItemButton>
                  </Tooltip>
                );
              }
              return (
                <Box key={group.title} sx={{ mb: 0.125 }}>
                  <Tooltip title={navigationCollapsed ? t(group.title) : ""} placement="right">
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
                      sx={{
                        borderRadius: "10px",
                        minHeight: 40,
                        px: navigationCollapsed ? 1.5 : 1,
                        justifyContent: navigationCollapsed ? "center" : "flex-start",
                        color: activeGroup ? "primary.main" : "text.secondary",
                      }}
                      aria-expanded={isExpanded}
                      aria-controls={groupId}
                    >
                      <ListItemIcon
                        sx={{
                          minWidth: navigationCollapsed ? 0 : 34,
                          color: "inherit",
                          justifyContent: "center",
                        }}
                      >
                        <NavIcon name={groupIcon} />
                      </ListItemIcon>
                      {!navigationCollapsed ? (
                        <>
                          <ListItemText
                            primary={t(group.title)}
                            primaryTypographyProps={{
                              variant: "overline",
                              fontWeight: activeGroup ? 700 : 600,
                              noWrap: true,
                            }}
                          />
                          <Typography component="span" variant="caption" aria-hidden="true" sx={{ ml: 0.5 }}>
                            {isExpanded ? "▾" : "▸"}
                          </Typography>
                        </>
                      ) : null}
                    </ListItemButton>
                  </Tooltip>
                  {isExpanded ? (
                    <List id={groupId} disablePadding sx={{ py: 0 }}>
                      {group.items.map((item) => {
                        const active = isNavItemActive(location.pathname, item.to);
                        return (
                          <Tooltip key={item.to} title={navigationCollapsed ? t(item.label) : ""} placement="right">
                            <ListItemButton
                              component={NavLink}
                              to={item.to}
                              onClick={() => setMobileNavOpen(false)}
                              selected={active}
                              aria-current={active ? "page" : undefined}
                              sx={{
                                borderRadius: "10px",
                                minHeight: 42,
                                mb: 0.125,
                                ml: navigationCollapsed ? 0 : 1,
                                px: navigationCollapsed ? 1.5 : 1,
                                justifyContent: navigationCollapsed ? "center" : "flex-start",
                                "&.Mui-selected": {
                                  color: "primary.main",
                                  bgcolor: "action.selected",
                                  "&::before": {
                                    content: '""',
                                    position: "absolute",
                                    left: 0,
                                    top: 8,
                                    bottom: 8,
                                    width: 3,
                                    borderRadius: 2,
                                    bgcolor: "primary.main",
                                  },
                                },
                              }}
                            >
                              <ListItemIcon
                                sx={{
                                  minWidth: navigationCollapsed ? 0 : 34,
                                  color: active ? "primary.main" : "text.secondary",
                                  justifyContent: "center",
                                }}
                              >
                                <NavIcon name={item.icon} />
                              </ListItemIcon>
                              {!navigationCollapsed ? (
                                <ListItemText
                                  primary={t(item.label)}
                                  primaryTypographyProps={{ variant: "body2", fontWeight: active ? 600 : 400 }}
                                />
                              ) : null}
                            </ListItemButton>
                          </Tooltip>
                        );
                      })}
                    </List>
                  ) : null}
                  {groupIndex < visibleNavGroups.length - 1 ? <Divider sx={{ my: 0.25, mx: 1 }} /> : null}
                </Box>
              );
            })}
          </Box>
          {!navigationCollapsed ? (
            <Typography variant="caption" color="text.secondary" sx={{ px: 1, pt: 1 }}>
              {t("navigation.note")}
            </Typography>
          ) : null}
        </Box>
      </Drawer>
      <Box
        component="main"
        id="admin-main-content"
        sx={{
          flex: 1,
          minWidth: 0,
          p: { xs: 2, md: 3 },
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
