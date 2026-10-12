import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { ProtectedRoute } from "./ProtectedRoute";
import { LoginPage } from "../features/auth/LoginPage";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { RoomsPage } from "../features/rooms/RoomsPage";
import { SessionsPage } from "../features/sessions/SessionsPage";
import { SessionDetailPage } from "../features/sessions/SessionDetailPage";
import { PlayersPage } from "../features/players/PlayersPage";
import { PlayerDetailPage } from "../features/players/PlayerDetailPage";
import { ClaimsPage } from "../features/claims/ClaimsPage";
import { DepositsPage } from "../features/deposits/DepositsPage";
import { AuditLogsPage } from "../features/audit/AuditLogsPage";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="rooms" element={<RoomsPage />} />
          <Route path="sessions" element={<SessionsPage />} />
          <Route path="sessions/:sessionId" element={<SessionDetailPage />} />
          <Route path="players" element={<PlayersPage />} />
          <Route path="players/:playerId" element={<PlayerDetailPage />} />
          <Route path="claims" element={<ClaimsPage />} />
          <Route path="deposits" element={<DepositsPage />} />
          <Route path="audit-logs" element={<AuditLogsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
