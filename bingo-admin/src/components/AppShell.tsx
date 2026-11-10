import {
  AppBar,
  Avatar,
  Box,
  Breadcrumbs,
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
  Link,
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
const navGroupAccents: Record<string, { color: string; background: string }> = {
  "nav.overview": { color: "#8fc5ff", background: "rgba(80, 153, 255, 0.18)" },
  "nav.operations": { color: "#62dec5", background: "rgba(47, 201, 168, 0.16)" },
  "nav.reviews": { color: "#ffc276", background: "rgba(241, 161, 67, 0.17)" },
  "nav.accounts": { color: "#c4a4ff", background: "rgba(159, 111, 240, 0.18)" },
  "nav.finance": { color: "#74d6ff", background: "rgba(71, 185, 232, 0.17)" },
  "nav.administration": { color: "#ff9eae", background: "rgba(234, 104, 132, 0.17)" },
};

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
      { to: "/wallet/signup-reward", label: "nav.signupReward", icon: "wallet" },
      { to: "/wallet/signup-reward-grants", label: "nav.signupRewardGrants", icon: "wallet" },
      { to: "/wallet/transfer-policy", label: "nav.transferPolicy", icon: "transfers" },
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

function NavIcon({ name, size = 20 }: { name: NavIconName; size?: number }) {
  return (
    <Box component="svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false" sx={{ width: size, height: size, flexShrink: 0 }}>
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
  if (itemPath === "/sessions" && pathname === "/session-tickets") {
    return true;
  }
  return pathname === itemPath || pathname.startsWith(`${itemPath}/`);
}

const routeTitles: Array<{ match: RegExp; title: string; parent?: string; parentTo?: string }> = [
  { match: /^\/dashboard$/, title: "nav.dashboard" },
  { match: /^\/sessions\/new$/, title: "Create session", parent: "nav.sessions", parentTo: "/sessions" },
  { match: /^\/sessions\/[^/]+$/, title: "Session details", parent: "nav.sessions", parentTo: "/sessions" },
  { match: /^\/session-tickets$/, title: "Session ticket lookup", parent: "nav.sessions", parentTo: "/sessions" },
  { match: /^\/sessions$/, title: "nav.sessions" },
  { match: /^\/rooms\/new$/, title: "Create room", parent: "nav.rooms", parentTo: "/rooms" },
  { match: /^\/rooms\/[^/]+\/edit$/, title: "Edit room", parent: "nav.rooms", parentTo: "/rooms" },
  { match: /^\/rooms$/, title: "nav.rooms" },
  { match: /^\/players\/[^/]+$/, title: "Player details", parent: "nav.players", parentTo: "/players" },
  { match: /^\/players$/, title: "nav.players" },
  { match: /^\/agents\/[^/]+$/, title: "Agent details", parent: "nav.agents", parentTo: "/agents" },
  { match: /^\/agents$/, title: "nav.agents" },
  { match: /^\/support\/tickets\/[^/]+$/, title: "Ticket details", parent: "nav.support", parentTo: "/support/tickets" },
  { match: /^\/support\/tickets$/, title: "nav.support" },
  { match: /^\/change-password$/, title: "Change password", parent: "Profile", parentTo: "/profile" },
  { match: /^\/profile$/, title: "Profile" },
  { match: /^\/community$/, title: "nav.community" },
  { match: /^\/deposits$/, title: "nav.deposits" },
  { match: /^\/claims$/, title: "nav.claims" },
  { match: /^\/prize-entitlements$/, title: "nav.entitlements" },
  { match: /^\/player-verifications$/, title: "nav.verification" },
  { match: /^\/wallet\/signup-reward$/, title: "nav.signupReward" },
  { match: /^\/wallet\/signup-reward-grants$/, title: "nav.signupRewardGrants" },
  { match: /^\/wallet\/transfer-policy$/, title: "nav.transferPolicy" },
  { match: /^\/wallet\/transfers$/, title: "nav.transfers" },
  { match: /^\/admin-users$/, title: "nav.adminUsers" },
  { match: /^\/admin-users\/new$/, title: "Create admin user", parent: "nav.adminUsers", parentTo: "/admin-users" },
  { match: /^\/admin-users\/[^/]+\/edit$/, title: "Edit admin user", parent: "nav.adminUsers", parentTo: "/admin-users" },
  { match: /^\/audit-logs$/, title: "nav.audit" },
  { match: /^\/reports$/, title: "nav.reports" },
];

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
  const pageTrail = useMemo(
    () => routeTitles.find((route) => route.match.test(location.pathname)),
    [location.pathname],
  );

  useEffect(() => {
    if (pageTrail) {
      document.title = `${t(pageTrail.title)} | ${t("brand")}`;
    }
  }, [pageTrail, t]);
  const supportOnly = Boolean(session?.user.roles.includes("SUPPORT") && !session.user.roles.includes("ADMIN"));
  const visibleNavGroups = useMemo(
    () => supportOnly
      ? [
          {
            title: "nav.operations",
            items: [
              { to: "/rooms", label: "nav.rooms", icon: "rooms" as const },
            ],
          },
          {
            title: "nav.reviews",
            items: [
              { to: "/support/tickets", label: "nav.support", icon: "support" as const },
              { to: "/session-tickets", label: "nav.sessionTickets", icon: "sessions" as const },
              { to: "/audit-logs", label: "nav.audit", icon: "audit" as const },
              { to: "/reports", label: "nav.reports", icon: "audit" as const },
            ],
          },
        ]
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
    <Box sx={{ display: "flex", minHeight: "100dvh", bgcolor: "background.default" }}>
      <CssBaseline />
      <Link
        href="#admin-main-content"
        sx={{
          position: "fixed",
          zIndex: (theme: Theme) => theme.zIndex.tooltip + 1,
          top: 8,
          left: 8,
          px: 2,
          py: 1,
          borderRadius: 2,
          bgcolor: "background.paper",
          boxShadow: 3,
          transform: "translateY(-160%)",
          "&:focus": { transform: "translateY(0)" },
        }}
      >{t("Skip to main content")}</Link>
      <AppBar position="fixed" sx={{ zIndex: (theme: Theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 1, px: { xs: 1, sm: 2 } }}>
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
          <Box
            aria-hidden="true"
            sx={{ width: 34, height: 34, display: "grid", placeItems: "center", borderRadius: 2.25, bgcolor: "primary.main", color: "#fff", fontSize: 17, fontWeight: 800, letterSpacing: "-0.06em", flexShrink: 0 }}
          >S</Box>
          <Stack spacing={0} sx={{ flexGrow: 1, minWidth: 0 }}>
            <Typography variant="subtitle1" noWrap sx={{ color: "text.primary", fontWeight: 750, lineHeight: 1.2 }}>
              {t("brand")}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: { xs: "none", sm: "block" } }}>
              {t("Operations console")}
            </Typography>
          </Stack>
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
              border: "1px solid",
              borderColor: "divider",
              textTransform: "none",
              color: "text.primary",
              maxWidth: { xs: 156, sm: 230 },
              minWidth: 0,
              "&:hover": { bgcolor: "action.hover" },
            }}
          >
            <Stack direction="row" spacing={1} alignItems="center">
              <Avatar sx={{ width: 30, height: 30, bgcolor: "primary.light", color: "primary.dark", fontSize: 13, fontWeight: 700 }}>
                {session?.user.displayName?.slice(0, 1)?.toUpperCase() ?? "A"}
              </Avatar>
              <Stack spacing={0} alignItems="flex-start">
                <Typography variant="body2" lineHeight={1.2} noWrap sx={{ maxWidth: { xs: 88, sm: 160 } }}>
                  {session?.user.displayName ?? t("account")}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2, maxWidth: 160, display: { xs: "none", sm: "block" } }}>
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
            color: "#e7eefb",
            backgroundColor: "#111d36",
            backgroundImage: "linear-gradient(180deg, #172746 0%, #111d36 38%, #101a31 100%)",
            borderRight: "1px solid #263858",
            transition: (theme: Theme) => theme.transitions.create("width"),
            "& .MuiIconButton-root": {
              color: "#a9bad4",
              "&:hover": { color: "#fff", bgcolor: "rgba(255,255,255,0.08)" },
            },
          },
        }}
      >
        <Toolbar />
        <Box
          component="nav"
          id="admin-primary-navigation"
          aria-label={t("navigation.primary")}
          sx={{ display: "flex", flex: 1, minHeight: 0, flexDirection: "column", overflowY: "auto", px: 1.25, pb: 1.5 }}
        >
          <Stack
            direction={navigationCollapsed ? "column" : "row"}
            alignItems="center"
            justifyContent={navigationCollapsed ? "center" : "space-between"}
            sx={{ px: navigationCollapsed ? 0 : 0.75, py: 1, mb: 0.5 }}
          >
            {!navigationCollapsed ? (
              <Typography variant="overline" noWrap sx={{ color: "#9bacc7", fontSize: "0.72rem", fontWeight: 750, letterSpacing: "0.12em" }}>
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
              const accent = navGroupAccents[group.title] ?? navGroupAccents["nav.operations"];
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
                        position: "relative",
                        borderRadius: "12px",
                        minHeight: 44,
                        mb: 0.75,
                        px: navigationCollapsed ? 1.5 : 1,
                        justifyContent: navigationCollapsed ? "center" : "flex-start",
                        color: active ? accent.color : "#d5deed",
                        transition: "background-color 140ms ease, color 140ms ease",
                        "&:hover": { bgcolor: "rgba(255,255,255,0.07)" },
                        "&.Mui-selected": {
                          color: accent.color,
                          bgcolor: accent.background,
                          "&::before": {
                            content: '""',
                            position: "absolute",
                            left: -10,
                            top: 9,
                            bottom: 9,
                            width: 3,
                            borderRadius: "0 4px 4px 0",
                            bgcolor: accent.color,
                          },
                        },
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: navigationCollapsed ? 0 : 38, color: "inherit", justifyContent: "center" }}>
                        <NavIcon name={item.icon} />
                      </ListItemIcon>
                      {!navigationCollapsed ? <ListItemText primary={t(item.label)} /> : null}
                    </ListItemButton>
                  </Tooltip>
                );
              }
              return (
                <Box key={group.title} sx={{ mb: 0.25 }}>
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
                        minHeight: navigationCollapsed ? 40 : 36,
                        mb: 0.5,
                        px: navigationCollapsed ? 1.5 : 1.25,
                        justifyContent: navigationCollapsed ? "center" : "flex-start",
                        color: activeGroup ? accent.color : "#d5deed",
                        transition: "background-color 140ms ease, color 140ms ease",
                        "&:hover": {
                          bgcolor: "rgba(255,255,255,0.06)",
                        },
                      }}
                      aria-expanded={isExpanded}
                      aria-controls={groupId}
                    >
                      <ListItemIcon
                        sx={{
                          minWidth: navigationCollapsed ? 0 : 34,
                          color: accent.color,
                          justifyContent: "center",
                          "& > svg": {
                            boxSizing: "content-box",
                            p: 0.6,
                            borderRadius: "9px",
                            backgroundColor: accent.background,
                          },
                        }}
                      >
                        <NavIcon name={groupIcon} size={16} />
                      </ListItemIcon>
                      {!navigationCollapsed ? (
                        <>
                          <ListItemText
                            primary={t(group.title)}
                            primaryTypographyProps={{
                              variant: "caption",
                              fontWeight: 700,
                              fontSize: "0.78rem",
                              letterSpacing: "0.055em",
                              lineHeight: 1.2,
                              noWrap: true,
                              sx: { color: "inherit" },
                            }}
                          />
                          <Typography component="span" variant="caption" aria-hidden="true" sx={{ ml: 0.5, color: "#8fa2c0" }}>
                            {isExpanded ? "▾" : "▸"}
                          </Typography>
                        </>
                      ) : null}
                    </ListItemButton>
                  </Tooltip>
                  {isExpanded ? (
                    <List
                      id={groupId}
                      disablePadding
                      sx={{
                        position: "relative",
                        py: 0.25,
                        ml: navigationCollapsed ? 0 : 1.5,
                        pl: navigationCollapsed ? 0 : 1.25,
                        borderLeft: navigationCollapsed ? "none" : "1px solid",
                        borderColor: "rgba(164, 185, 218, 0.18)",
                      }}
                    >
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
                                position: "relative",
                                borderRadius: "10px",
                                minHeight: 38,
                                mb: 0.25,
                                px: navigationCollapsed ? 1.5 : 0.75,
                                justifyContent: navigationCollapsed ? "center" : "flex-start",
                                color: active ? accent.color : "#bac8dc",
                                transition: "background-color 140ms ease, color 140ms ease",
                                "&:hover": { bgcolor: "rgba(255,255,255,0.07)" },
                                "&.Mui-selected": {
                                  color: accent.color,
                                  bgcolor: accent.background,
                                  "&::before": {
                                    content: '""',
                                    position: "absolute",
                                    left: -10,
                                    top: 8,
                                    bottom: 8,
                                    width: 3,
                                    borderRadius: "0 4px 4px 0",
                                    bgcolor: accent.color,
                                  },
                                },
                              }}
                            >
                              <ListItemIcon
                                sx={{
                                  minWidth: navigationCollapsed ? 0 : 34,
                                  color: active ? accent.color : "#91a3bd",
                                  justifyContent: "center",
                                }}
                              >
                                <NavIcon name={item.icon} size={18} />
                              </ListItemIcon>
                              {!navigationCollapsed ? (
                                <ListItemText
                                  primary={t(item.label)}
                                  primaryTypographyProps={{
                                    variant: "body2",
                                    fontWeight: active ? 650 : 450,
                                    sx: { color: "inherit", fontSize: "0.875rem", lineHeight: 1.35 },
                                  }}
                                />
                              ) : null}
                            </ListItemButton>
                          </Tooltip>
                        );
                      })}
                    </List>
                  ) : null}
                  {groupIndex < visibleNavGroups.length - 1 ? <Divider sx={{ my: 0.5, mx: 1, borderColor: "rgba(164, 185, 218, 0.13)" }} /> : null}
                </Box>
              );
            })}
          </Box>
          {!navigationCollapsed ? (
            <Typography variant="caption" sx={{ mx: 0.75, px: 0.25, pt: 1.5, mt: 1, color: "#91a3bd", borderTop: "1px solid rgba(164, 185, 218, 0.16)" }}>
              {t("navigation.note")}
            </Typography>
          ) : null}
        </Box>
      </Drawer>
      <Box
        component="main"
        id="admin-main-content"
        tabIndex={-1}
        sx={{
          flex: 1,
          minWidth: 0,
          px: { xs: 1.5, sm: 2.5, lg: 3.5 },
          pb: { xs: 3, md: 4 },
        }}
      >
        <Toolbar />
          <Box className="admin-page-content" sx={{ width: "100%", minWidth: 0, mx: "auto" }}>
          {pageTrail ? (
            <Breadcrumbs aria-label={t("Breadcrumbs")} sx={{ mb: 1.5, color: "text.secondary", "& .MuiBreadcrumbs-separator": { mx: 1 } }}>
              {pageTrail.parent && pageTrail.parentTo ? (
                <Link component={NavLink} to={`${pageTrail.parentTo}${location.search}`} underline="hover" color="inherit" sx={{ fontSize: "0.79rem", fontWeight: 550 }}>
                  {t(pageTrail.parent)}
                </Link>
              ) : null}
              <Typography color="text.primary" variant="body2" aria-current="page" sx={{ fontWeight: 650 }}>
                {t(pageTrail.title)}
              </Typography>
            </Breadcrumbs>
          ) : null}
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
