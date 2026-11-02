import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { refreshAdminUserProfile } from "../api/admin";
import { clearSession, hasRequiredRole, readSession, useSession, writeSession } from "../api/session";
import { StatusState } from "../components/StatusState";
import { getUserFacingErrorMessage, parseApiError } from "../api/types";

interface ProtectedRouteProps {
  allowedRoles?: string[];
  refreshSession?: boolean;
}

export function ProtectedRoute({ allowedRoles = ["ADMIN"], refreshSession = false }: ProtectedRouteProps) {
  const session = useSession();
  const location = useLocation();
  const [verifiedToken, setVerifiedToken] = useState<string | null>(null);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [refreshAttempt, setRefreshAttempt] = useState(0);

  useEffect(() => {
    if (!refreshSession || !session?.accessToken) {
      setVerifiedToken(session?.accessToken ?? null);
      return;
    }
    let active = true;
    setVerifiedToken(null);
    setRefreshError(null);
    refreshAdminUserProfile()
      .then((user) => {
        if (!active) return;
        const latestSession = readSession();
        if (latestSession?.accessToken === session.accessToken) {
          writeSession({ ...latestSession, user });
        }
        setVerifiedToken(session.accessToken);
      })
      .catch((error: unknown) => {
        if (!active) return;
        const parsed = parseApiError(error);
        if (parsed.status === 401) {
          clearSession({ expired: true });
          return;
        }
        setRefreshError(getUserFacingErrorMessage(parsed) ?? "Something went wrong. Please try again.");
        setVerifiedToken(session.accessToken);
      });
    return () => { active = false; };
  }, [refreshAttempt, refreshSession, session?.accessToken]);

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (refreshSession && refreshError) {
    return (
      <StatusState
        title="Unable to verify access"
        message={refreshError}
        actionLabel="Retry"
        onAction={() => setRefreshAttempt((attempt) => attempt + 1)}
      />
    );
  }

  if (refreshSession && verifiedToken !== session.accessToken) {
    return <StatusState title="Checking access" loading message="Refreshing your account permissions" />;
  }

  if (!hasRequiredRole(session.user.roles, allowedRoles)) {
    return (
      <StatusState
        title="Access denied"
        message="Your account is authenticated but does not have a required admin role."
      />
    );
  }

  if (session.user.mustChangePassword && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }

  return <Outlet />;
}
