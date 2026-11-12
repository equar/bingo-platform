import { useTranslation } from "react-i18next";
import { lazy, Suspense, useEffect } from "react";
import { Box, CircularProgress } from "@mui/material";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { ProtectedRoute } from "./ProtectedRoute";
import { useSession } from "../api/session";

const AppShell = lazy(() => import("../components/AppShell").then((module) => ({ default: module.AppShell })));
const LoginPage = lazy(() => import("../features/auth/LoginPage").then((module) => ({ default: module.LoginPage })));
const AdminAccountActivationPage = lazy(() =>
  import("../features/auth/AdminAccountActivationPage").then((module) => ({
    default: module.AdminAccountActivationPage,
  })),
);
const DashboardPage = lazy(() =>
  import("../features/dashboard/DashboardPage").then((module) => ({ default: module.DashboardPage })),
);
const AdminUsersPage = lazy(() =>
  import("../features/adminUsers/AdminUsersPage").then((module) => ({ default: module.AdminUsersPage })),
);
const RoomsPage = lazy(() => import("../features/rooms/RoomsPage").then((module) => ({ default: module.RoomsPage })));
const SessionsPage = lazy(() =>
  import("../features/sessions/SessionsPage").then((module) => ({ default: module.SessionsPage })),
);
const SessionCreatePage = lazy(() =>
  import("../features/sessions/SessionCreatePage").then((module) => ({ default: module.SessionCreatePage })),
);
const SessionDetailPage = lazy(() =>
  import("../features/sessions/SessionDetailPage").then((module) => ({ default: module.SessionDetailPage })),
);
const SessionTicketsPage = lazy(() =>
  import("../features/sessions/SessionTicketsPage").then((module) => ({ default: module.SessionTicketsPage })),
);
const PlayersPage = lazy(() =>
  import("../features/players/PlayersPage").then((module) => ({ default: module.PlayersPage })),
);
const PlayerVerificationPage = lazy(() =>
  import("../features/verification/PlayerVerificationPage").then((module) => ({
    default: module.PlayerVerificationPage,
  })),
);
const PlayerDetailPage = lazy(() =>
  import("../features/players/PlayerDetailPage").then((module) => ({ default: module.PlayerDetailPage })),
);
const ReportsPage = lazy(() =>
  import("../features/reports/ReportsPage").then((module) => ({ default: module.ReportsPage })),
);
const ClaimsPage = lazy(() =>
  import("../features/claims/ClaimsPage").then((module) => ({ default: module.ClaimsPage })),
);
const PrizeEntitlementsPage = lazy(() =>
  import("../features/prizeEntitlements/PrizeEntitlementsPage").then((module) => ({
    default: module.PrizeEntitlementsPage,
  })),
);
const DepositsPage = lazy(() =>
  import("../features/deposits/DepositsPage").then((module) => ({ default: module.DepositsPage })),
);
const DepositDetailPage = lazy(() =>
  import("../features/deposits/DepositDetailPage").then((module) => ({ default: module.DepositDetailPage })),
);
const AuditLogsPage = lazy(() =>
  import("../features/audit/AuditLogsPage").then((module) => ({ default: module.AuditLogsPage })),
);
const AgentsPage = lazy(() =>
  import("../features/agents/AgentsPage").then((module) => ({ default: module.AgentsPage })),
);
const AgentDetailPage = lazy(() =>
  import("../features/agents/AgentDetailPage").then((module) => ({ default: module.AgentDetailPage })),
);
const SupportTicketsPage = lazy(() =>
  import("../features/support/SupportTicketsPage").then((module) => ({ default: module.SupportTicketsPage })),
);
const SupportTicketDetailPage = lazy(() =>
  import("../features/support/SupportTicketDetailPage").then((module) => ({ default: module.SupportTicketDetailPage })),
);
const ProfilePage = lazy(() =>
  import("../features/profile/ProfilePage").then((module) => ({ default: module.ProfilePage })),
);
const ChangePasswordPage = lazy(() =>
  import("../features/profile/ChangePasswordPage").then((module) => ({ default: module.ChangePasswordPage })),
);
const WalletPoliciesPage = lazy(() =>
  import("../features/wallet/WalletPoliciesPage").then((module) => ({ default: module.WalletPoliciesPage })),
);
const WalletTransfersPage = lazy(() =>
  import("../features/wallet/WalletTransfersPage").then((module) => ({ default: module.WalletTransfersPage })),
);
const SignupRewardGrantsPage = lazy(() =>
  import("../features/wallet/SignupRewardGrantsPage").then((module) => ({ default: module.SignupRewardGrantsPage })),
);

const CommunityPage = lazy(() => import("../features/community/CommunityPage").then(module => ({ default: module.CommunityPage })));
const PlayerAppPage = lazy(() => import("../features/playerApp/PlayerAppPage").then(module => ({ default: module.PlayerAppPage })));

export function AppRouter() {
  const { t } = useTranslation();
  const location = useLocation();
  useEffect(() => {
    const title = location.pathname === "/login"
      ? "auth.signInTitle"
      : location.pathname === "/reset-password" ? "auth.resetPasswordTitle"
        : location.pathname === "/activate-account" ? "auth.activateAccountTitle" : null;
    if (title) document.title = `${t(title)} | ${t("brand")}`;
  }, [location.pathname, t]);
  return (
    <Suspense
      fallback={
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress aria-label={t("Loading page")} />
        </Box>
      }
    >
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/activate-account" element={<AdminAccountActivationPage />} />
        <Route path="/reset-password" element={<AdminAccountActivationPage resetPassword />} />
        <Route element={<ProtectedRoute allowedRoles={["ADMIN", "SUPPORT"]} refreshSession />}>
          <Route element={<AppShell />}>
            <Route index element={<RoleHomeRedirect />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="change-password" element={<ChangePasswordPage />} />
            <Route path="support/tickets" element={<SupportTicketsPage />} />
            <Route path="support/tickets/:ticketId" element={<SupportTicketDetailPage />} />
            <Route path="session-tickets" element={<SessionTicketsPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="deposits/:depositId" element={<DepositDetailPage />} />
            <Route element={<ProtectedRoute allowedRoles={["ADMIN", "SUPPORT"]} />}>
              <Route path="management" element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="community" element={<CommunityPage />} />
              <Route path="sessions" element={<SessionsPage />} />
              <Route path="sessions/new" element={<SessionCreatePage />} />
              <Route path="sessions/:sessionId" element={<SessionDetailPage />} />
              <Route path="players" element={<PlayersPage />} />
              <Route path="player-verifications" element={<PlayerVerificationPage />} />
              <Route path="players/:playerId" element={<PlayerDetailPage />} />
              <Route path="deposits" element={<DepositsPage />} />
              <Route path="agents" element={<AgentsPage />} />
              <Route path="agents/:agentId" element={<AgentDetailPage />} />
              <Route path="wallet" element={<WalletHomeRedirect />} />
              <Route path="wallet/transfers" element={<WalletTransfersPage />} />
              <Route path="wallet/signup-reward-grants" element={<SignupRewardGrantsPage />} />
            </Route>
            <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
              <Route path="player-app" element={<PlayerAppPage />} />
              <Route path="audit-logs" element={<AuditLogsPage />} />
              <Route path="admin-users" element={<AdminUsersPage />} />
              <Route path="admin-users/new" element={<AdminUsersPage />} />
              <Route path="admin-users/:userId/edit" element={<AdminUsersPage />} />
              <Route path="rooms" element={<RoomsPage />} />
              <Route path="rooms/new" element={<RoomsPage />} />
              <Route path="rooms/:roomId/edit" element={<RoomsPage />} />
              <Route path="claims" element={<ClaimsPage />} />
              <Route path="prize-entitlements" element={<PrizeEntitlementsPage />} />
              <Route path="wallet/signup-reward" element={<WalletPoliciesPage />} />
              <Route path="wallet/transfer-policy" element={<WalletPoliciesPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<RoleHomeRedirect />} />
      </Routes>
    </Suspense>
  );
}

function RoleHomeRedirect() {
  return <Navigate to="/dashboard" replace />;
}

function WalletHomeRedirect() {
  const session = useSession();
  const supportOnly = session?.user.roles.includes("SUPPORT") && !session.user.roles.includes("ADMIN");
  return <Navigate to={supportOnly ? "/wallet/transfers" : "/wallet/signup-reward"} replace />;
}
